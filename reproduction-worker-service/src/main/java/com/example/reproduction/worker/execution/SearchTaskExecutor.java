package com.example.reproduction.worker.execution;

import com.example.reproduction.algorithm.graph.DefaultDependencyRules;
import com.example.reproduction.algorithm.graph.DependencyAnalysis;
import com.example.reproduction.algorithm.graph.DependencyAnalyzer;
import com.example.reproduction.algorithm.search.*;
import com.example.reproduction.domain.*;
import com.example.reproduction.evaluation.*;
import com.example.reproduction.messaging.CandidateTask;
import com.example.reproduction.messaging.JobContext;
import com.example.reproduction.messaging.TaskResult;
import com.example.reproduction.util.Hashing;
import com.example.reproduction.worker.cache.RedisEvaluationCache;
import com.example.reproduction.worker.config.WorkerProperties;
import com.example.reproduction.worker.oracle.HttpBugOracle;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Runs ONE search task: "reduce the job's input, excluding the banned fields". This is where the pure-Java
 * engine meets the infrastructure (HTTP oracle, Redis cache, Kafka events).
 *
 * Concurrency: evaluations of one task run on a bounded pool sized min(job.maxConcurrentEvaluations,
 * worker.max-evaluation-threads); the number of tasks in flight per worker is bounded by the Kafka listener
 * concurrency (max.poll.records=1), so total parallelism is bounded by construction (backpressure = Kafka lag).
 */
@Component
public class SearchTaskExecutor {
    private static final Logger log = LoggerFactory.getLogger(SearchTaskExecutor.class);

    private final JobContextClient contexts;
    private final StringRedisTemplate redis;
    private final KafkaTemplate<String, Object> kafka;
    private final MeterRegistry meters;
    private final WebClient simulator;
    private final ObjectMapper mapper;
    private final WorkerProperties props;

    public SearchTaskExecutor(JobContextClient contexts, StringRedisTemplate redis, KafkaTemplate<String, Object> kafka,
                              MeterRegistry meters, WebClient simulatorWebClient, ObjectMapper mapper, WorkerProperties props) {
        this.contexts = contexts; this.redis = redis; this.kafka = kafka; this.meters = meters;
        this.simulator = simulatorWebClient; this.mapper = mapper; this.props = props;
    }

    public TaskResult execute(CandidateTask task) {
        JobContext ctx = contexts.load(task.jobId());
        SearchConfig cfg = ctx.config();
        FieldUniverse universe = FieldUniverse.ofNested(ctx.initialInput());
        DependencyAnalysis analysis = new DependencyAnalyzer(DefaultDependencyRules.paymentDomain()).analyze(universe.paths(), ctx.dependencies());
        BitSet banned = task.bannedFields() == null ? new BitSet() : universe.of(task.bannedFields());

        MetricsSearchListener listener = new MetricsSearchListener(kafka, meters, task, universe);
        CancellationProbe cancelled = new CancellationProbe(redis, task.jobId());
        Duration untilDeadline = task.deadline() == null ? cfg.maxExecutionTime() : Duration.between(Instant.now(), task.deadline());
        if (untilDeadline.isNegative() || untilDeadline.isZero()) return result(task, TaskResult.Outcome.PARTIAL_BUDGET, null, universe, null, 0, "deadline already passed");
        Duration budgetTime = untilDeadline.compareTo(cfg.maxExecutionTime()) < 0 ? untilDeadline : cfg.maxExecutionTime();

        BugOracle oracle = new HttpBugOracle(simulator, mapper, ctx.bugSignature(), ctx.scenarioId(), props.evaluationTimeout());
        EvaluationCache cache = new RedisEvaluationCache(redis, mapper, meters, props.l1CacheSize());
        EvaluationBudget budget = new EvaluationBudget(cfg.maxEvaluations(), budgetTime);
        CandidateEvaluator evaluator = new CandidateEvaluator(universe, oracle, cache, new DominanceIndex(), cfg, budget,
                namespace(ctx, cfg), listener, cancelled);

        int threads = Math.max(1, Math.min(cfg.maxConcurrentEvaluations(), props.maxEvaluationThreads()));
        ThreadPoolExecutor pool = threads > 1 ? ParallelExecutors.bounded(threads, "eval-" + task.taskId().substring(0, 6)) : null;
        try {
            ReductionContext rc = new ReductionContext(universe, analysis, evaluator, cfg, universe.all(), banned, listener, pool,
                    ctx.keepHints() == null ? Map.of() : ctx.keepHints());
            ReductionOutcome o = ReductionEngine.strategyFor(cfg.strategy()).reduce(rc);

            if (!o.startReproduced()) {
                TaskResult.Outcome out = o.startStatus() == EvaluationStatus.DOES_NOT_REPRODUCE ? TaskResult.Outcome.START_NOT_REPRODUCING : TaskResult.Outcome.START_INCONCLUSIVE;
                return result(task, out, o, universe, evaluator, 0, "start candidate status " + o.startStatus());
            }
            double rate = 0;
            boolean proven = o.provenMinimal();
            if (o.stopReason() == StopReason.COMPLETED) {
                EvaluationResult conf = evaluator.confirm(o.fields()); // real reproduction probability (all attempts)
                rate = conf.reproductionRate();
                proven = proven && conf.reproduces();
                DistributionSummary.builder("minimal.candidate.size").register(meters).record(o.fields().cardinality());
            }
            TaskResult.Outcome out = switch (o.stopReason()) {
                case COMPLETED -> TaskResult.Outcome.SUCCESS;
                case CANCELLED -> TaskResult.Outcome.CANCELLED;
                default -> TaskResult.Outcome.PARTIAL_BUDGET;
            };
            ReductionOutcome finalO = new ReductionOutcome(o.fields(), o.startStatus(), proven, o.steps(), o.proof(), o.stopReason(), o.iterations());
            return result(task, out, finalO, universe, evaluator, rate, o.stopReason().name());
        } finally {
            if (pool != null) pool.shutdownNow();
        }
    }

    /** Results are only shareable between searches that agree on scenario, signature and flakiness policy. */
    static String namespace(JobContext ctx, SearchConfig cfg) {
        return Hashing.sha256Hex(ctx.scenarioId() + "|" + ctx.bugSignature() + "|" + cfg.evaluationAttempts() + "|" + cfg.minimumReproductionRate()).substring(0, 16);
    }

    private TaskResult result(CandidateTask t, TaskResult.Outcome outcome, ReductionOutcome o, FieldUniverse u,
                              CandidateEvaluator ev, double rate, String detail) {
        List<String> minimal = o == null ? List.of() : u.pathsOf(o.fields());
        return new TaskResult(t.jobId(), t.taskId(), t.candidateId(), t.candidateHash(), t.attempt(), Instant.now(), t.correlationId(),
                outcome, minimal, o != null && o.provenMinimal(), rate, o == null ? List.of() : o.steps(), o == null ? List.of() : o.proof(),
                o == null ? StopReason.COMPLETED : o.stopReason(), detail,
                ev == null ? 0 : ev.stats().candidateEvaluations.get(), ev == null ? 0 : ev.stats().cacheHits.get());
    }
}
