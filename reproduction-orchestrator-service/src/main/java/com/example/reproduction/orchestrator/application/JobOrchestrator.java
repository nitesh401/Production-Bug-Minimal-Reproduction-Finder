package com.example.reproduction.orchestrator.application;

import com.example.reproduction.domain.*;
import com.example.reproduction.algorithm.search.MinimalEnumeration;
import com.example.reproduction.messaging.*;
import com.example.reproduction.orchestrator.config.OrchestratorProperties;
import com.example.reproduction.persistence.entity.*;
import com.example.reproduction.persistence.repository.*;
import com.example.reproduction.platform.kafka.PoisonMessageException;
import com.example.reproduction.util.CanonicalJson;
import com.example.reproduction.util.InputFlattener;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Job lifecycle + task graph. Everything here is idempotent, because Kafka delivers at least once:
 * <ul>
 *   <li>SUBMIT: only a PENDING job is started; duplicates are ignored;</li>
 *   <li>task ids are deterministic (hash of jobId + banned fields) so creating a task twice is a no-op;</li>
 *   <li>results: applied once per task (COMPLETED tasks ignore later results); stale-attempt results are accepted
 *       because evaluation is deterministic, but counted.</li>
 * </ul>
 * Kafka publishes happen after commit ({@link AfterCommit}); a lost publish is repaired by the lease sweeper.
 */
@Service
public class JobOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(JobOrchestrator.class);
    private static final List<WorkerTaskEntity.Status> OUTSTANDING = List.of(WorkerTaskEntity.Status.PENDING, WorkerTaskEntity.Status.RUNNING);

    private final ReproductionJobRepository jobs;
    private final InputFieldRepository fields;
    private final InputDependencyRepository deps;
    private final WorkerTaskRepository tasks;
    private final ReductionStepRepository steps;
    private final JobAttemptRepository attempts;
    private final JobStateMachine machine;
    private final AnalysisClient analysis;
    private final JobContextService contexts;
    private final TaskPublisher publisher;
    private final ProgressTracker progress;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;
    private final OrchestratorProperties props;
    private final TransactionTemplate tx;

    public JobOrchestrator(ReproductionJobRepository jobs, InputFieldRepository fields, InputDependencyRepository deps,
                           WorkerTaskRepository tasks, ReductionStepRepository steps, JobAttemptRepository attempts,
                           JobStateMachine machine, AnalysisClient analysis, JobContextService contexts, TaskPublisher publisher,
                           ProgressTracker progress, KafkaTemplate<String, Object> kafka, ObjectMapper mapper, MeterRegistry meters,
                           OrchestratorProperties props, TransactionTemplate tx) {
        this.jobs = jobs; this.fields = fields; this.deps = deps; this.tasks = tasks; this.steps = steps; this.attempts = attempts;
        this.machine = machine; this.analysis = analysis; this.contexts = contexts; this.publisher = publisher; this.progress = progress;
        this.kafka = kafka; this.mapper = mapper; this.meters = meters; this.props = props; this.tx = tx;
    }

    // ------------------------------------------------------------------ SUBMIT

    public void handleSubmit(String jobId, String correlationId) {
        // tx 1: claim the job and materialise its fields
        Map<String, Object> input = tx.execute(s -> {
            ReproductionJobEntity job = jobs.findById(jobId).orElseThrow(() -> new PoisonMessageException("SUBMIT for unknown job " + jobId));
            if (job.status != JobState.PENDING) { log.info("SUBMIT ignored, job already {}", job.status); return null; }
            machine.transition(job, JobState.ANALYZING, Instant.now());
            Map<String, Object> in = parse(job.initialInputJson);
            if (fields.countByJobId(jobId) == 0) {
                Map<String, Object> flat = new TreeMap<>(InputFlattener.flatten(in));
                int i = 0;
                for (Map.Entry<String, Object> e : flat.entrySet()) {
                    String json = CanonicalJson.write(e.getValue());
                    fields.save(new InputFieldEntity(jobId, i++, e.getKey(), json, e.getKey().length() + json.length()));
                }
                job.originalFieldCount = flat.size();
            }
            attempts.save(new JobAttemptEntity(jobId, nextAttemptNo(jobId), "SUBMIT", Instant.now()));
            jobs.save(job);
            return in;
        });
        if (input == null) return;

        // no transaction while calling another service
        DependencyAnalysisView view = analysis.analyze(input);

        // tx 2: persist dependencies, publish the primary task
        tx.executeWithoutResult(s -> {
            ReproductionJobEntity job = jobs.findById(jobId).orElseThrow();
            List<InputFieldEntity> fs = fields.findByJobIdOrderByIdx(jobId);
            Map<String, Integer> idx = new HashMap<>();
            fs.forEach(f -> idx.put(f.path, f.idx));
            if (deps.findByJobId(jobId).isEmpty())
                for (Dependency d : view.dependencies()) {
                    Integer a = idx.get(d.from()), b = idx.get(d.to());
                    if (a != null && b != null) deps.save(new InputDependencyEntity(jobId, a, b, d.reason()));
                }
            JobContext ctx = contexts.build(jobId, view.keepHints() == null ? Map.of() : view.keepHints());
            contexts.cache(ctx);
            machine.transition(job, JobState.SEARCHING, Instant.now());
            job.deadlineAt = Instant.now().plus(ctx.config().maxExecutionTime());
            jobs.save(job);
            createAndPublish(job, List.of(), 1, correlationId);
            progress.set(jobId, Map.of("state", "SEARCHING", "originalFields", Integer.toString(job.originalFieldCount)));
        });
        meters.counter("reproduction.jobs").increment();
    }

    // ------------------------------------------------------------------ RESULTS

    public void handleResult(TaskResult r) {
        tx.executeWithoutResult(s -> {
            WorkerTaskEntity t = tasks.findById(r.taskId()).orElse(null);
            if (t == null) { meters.counter("results.unknown.task").increment(); log.warn("result for unknown task {} ignored", r.taskId()); return; }
            if (t.status == WorkerTaskEntity.Status.COMPLETED || t.status == WorkerTaskEntity.Status.DEAD) {
                meters.counter("results.duplicate").increment(); return; // idempotent
            }
            ReproductionJobEntity job = jobs.findById(t.jobId).orElseThrow();
            if (job.status.terminal()) { log.info("result for terminal job {} ignored", job.status); return; }
            if (r.attempt() < t.attempt) meters.counter("results.stale.attempt").increment();

            switch (r.outcome()) {
                case SUCCESS, PARTIAL_BUDGET -> complete(t, r, job);
                case START_NOT_REPRODUCING -> {
                    t.status = WorkerTaskEntity.Status.COMPLETED; t.resultJson = json(r); t.updatedAt = Instant.now();
                    if ("PRIMARY".equals(t.type)) fail(job, "The full input does not reproduce the bug (evaluated as DOES_NOT_REPRODUCE)");
                }
                case START_INCONCLUSIVE, FAILED -> retryOrGiveUp(t, job, r.detail(), r.correlationId());
                case CANCELLED -> { t.status = WorkerTaskEntity.Status.DEAD; t.resultJson = json(r); t.updatedAt = Instant.now(); }
            }
            tasks.save(t);
            if (!job.status.terminal()) maybeFinalize(job, r.correlationId());
            refreshProgress(job);
        });
    }

    private void complete(WorkerTaskEntity t, TaskResult r, ReproductionJobEntity job) {
        t.status = WorkerTaskEntity.Status.COMPLETED;
        t.resultJson = json(r);
        t.updatedAt = Instant.now();
        for (ReductionStep st : r.steps())
            if (!steps.existsByJobIdAndTaskIdAndStepIndex(t.jobId, t.taskId, st.index())) {
                ReductionStepEntity e = new ReductionStepEntity(t.jobId, t.taskId, st.index());
                e.phase = st.phase(); e.action = st.action(); e.sizeBefore = st.sizeBefore(); e.sizeAfter = st.sizeAfter();
                e.granularity = st.granularity(); e.candidateHash = st.candidateHash(); e.createdAt = Instant.now();
                steps.save(e);
            }
        if (r.outcome() != TaskResult.Outcome.SUCCESS || r.minimalFields().isEmpty()) return;

        // is this a NEW minimal set? then branch on it (hitting-set enumeration of alternative minimal candidates)
        JobOptions opts = options(job);
        Set<String> known = completedSolutions(t.jobId, t.taskId);
        String key = String.join(",", r.minimalFields());
        if (known.contains(key)) return;
        known.add(key);
        if (known.size() >= opts.toSearchConfig().maxSolutions()) return;
        List<String> allFields = fields.findByJobIdOrderByIdx(t.jobId).stream().map(f -> f.path).toList();
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < allFields.size(); i++) idx.put(allFields.get(i), i);
        BitSet ban = new BitSet(), minimal = new BitSet();
        for (String b : publisher.bans(t)) ban.set(idx.get(b));
        for (String m : r.minimalFields()) minimal.set(idx.get(m));
        for (BitSet nb : MinimalEnumeration.nextBans(ban, minimal)) {
            if (tasks.findByJobId(t.jobId).size() >= props.maxTasksPerJob()) { log.warn("maxTasksPerJob reached, not branching further"); break; }
            List<String> bans = new ArrayList<>();
            for (int i = nb.nextSetBit(0); i >= 0; i = nb.nextSetBit(i + 1)) bans.add(allFields.get(i));
            createAndPublish(job, bans, 1, r.correlationId());
        }
    }

    private Set<String> completedSolutions(String jobId, String exceptTaskId) {
        Set<String> out = new HashSet<>();
        for (WorkerTaskEntity o : tasks.findByJobId(jobId)) {
            if (o.taskId.equals(exceptTaskId) || o.status != WorkerTaskEntity.Status.COMPLETED || o.resultJson == null) continue;
            TaskResult tr = read(o.resultJson, TaskResult.class);
            if (tr.outcome() == TaskResult.Outcome.SUCCESS && !tr.minimalFields().isEmpty()) out.add(String.join(",", tr.minimalFields()));
        }
        return out;
    }

    // ------------------------------------------------------------------ TASKS

    /** Deterministic task id => creating the same (job, bans) task twice is a no-op. */
    void createAndPublish(ReproductionJobEntity job, List<String> bans, int attempt, String correlationId) {
        List<String> sorted = bans.stream().sorted().toList();
        String taskId = UUID.nameUUIDFromBytes((job.id + "|" + String.join(",", sorted)).getBytes(StandardCharsets.UTF_8)).toString();
        if (tasks.existsById(taskId)) return;
        WorkerTaskEntity t = new WorkerTaskEntity(taskId);
        t.jobId = job.id; t.type = sorted.isEmpty() ? "PRIMARY" : "ALTERNATIVE"; t.status = WorkerTaskEntity.Status.PENDING;
        t.attempt = attempt; t.bannedJson = json(sorted); t.createdAt = Instant.now(); t.updatedAt = t.createdAt;
        publisher.publish(t, job, correlationId);
        tasks.save(t);
        meters.counter("tasks.created", "type", t.type).increment();
    }

    /** Called by the sweeper (lease expired => worker crashed / message lost) and for inconclusive results. */
    public void retryOrGiveUp(WorkerTaskEntity t, ReproductionJobEntity job, String reason, String correlationId) {
        if (t.attempt >= props.maxTaskAttempts()) {
            t.status = WorkerTaskEntity.Status.DEAD;
            t.updatedAt = Instant.now();
            meters.counter("tasks.dead").increment();
            log.error("task {} gave up after {} attempts: {}", t.taskId, t.attempt, reason);
            CandidateTask poison = new CandidateTask(t.jobId, t.taskId, null, null, t.attempt, Instant.now(), correlationId,
                    CandidateTask.Type.valueOf(t.type), publisher.bans(t), job.deadlineAt);
            AfterCommit.run(() -> kafka.send(Topics.DLQ, t.taskId, poison));
            if ("PRIMARY".equals(t.type)) fail(job, "primary search task failed after " + t.attempt + " attempts: " + reason);
        } else {
            t.attempt++;
            t.status = WorkerTaskEntity.Status.PENDING;
            meters.counter("kafka.retry.count").increment();
            log.warn("re-enqueuing task {} as attempt {} ({})", t.taskId, t.attempt, reason);
            publisher.publish(t, job, correlationId);
        }
    }

    // ------------------------------------------------------------------ FINALIZE

    private void maybeFinalize(ReproductionJobEntity job, String correlationId) {
        if (tasks.countByJobIdAndStatusIn(job.id, OUTSTANDING) > 0) return;
        finish(job, null, correlationId);
    }

    /** @param forced non-null to force a terminal state (TIMED_OUT), otherwise derived from the tasks. */
    public void finish(ReproductionJobEntity job, JobState forced, String correlationId) {
        if (job.status.terminal()) return;
        JobContext ctx = contexts.build(job.id, Map.of());
        List<WorkerTaskEntity> all = tasks.findByJobId(job.id);
        List<TaskResult> results = all.stream().filter(o -> o.resultJson != null).map(o -> read(o.resultJson, TaskResult.class)).toList();
        boolean incomplete = all.stream().anyMatch(o -> o.status != WorkerTaskEntity.Status.COMPLETED)
                || results.stream().anyMatch(r -> r.outcome() == TaskResult.Outcome.PARTIAL_BUDGET || r.outcome() == TaskResult.Outcome.START_INCONCLUSIVE);
        long evaluations = results.stream().mapToLong(TaskResult::evaluations).sum();
        FieldUniverse universe = FieldUniverse.ofNested(ctx.initialInput());
        JobResult result = ResultAssembler.assemble(universe, ctx.dependencies(), ctx.config(), results, evaluations, incomplete,
                forced == JobState.TIMED_OUT ? "maxExecutionTime exceeded; result is partial" : null);
        job.resultJson = json(result);
        JobState target = forced != null ? forced : result.minimalCandidates().isEmpty() ? JobState.FAILED : incomplete ? JobState.PARTIAL : JobState.COMPLETED;
        if (target == JobState.FAILED && job.errorMessage == null) job.errorMessage = "no reproducing candidate found";
        machine.transition(job, target, Instant.now());
        jobs.save(job);
        attempts.findFirstByJobIdOrderByAttemptNoDesc(job.id).ifPresent(a -> { a.endedAt = Instant.now(); a.outcome = target.name(); attempts.save(a); });
        if (target == JobState.COMPLETED || target == JobState.PARTIAL) {
            meters.counter("reproduction.jobs.completed").increment();
            List<List<String>> mins = result.minimalCandidates().stream().map(MinimalCandidate::fieldPaths).toList();
            AnalysisEvent ev = new AnalysisEvent(job.id, correlationId, Instant.now(), universe.paths(), mins);
            AfterCommit.run(() -> kafka.send(Topics.ANALYSIS, job.id, ev));
        }
        progress.set(job.id, Map.of("state", target.name()));
        log.info("job {} finished as {} ({} minimal candidates)", job.id, target, result.equivalentMinimalCandidates());
    }

    private void fail(ReproductionJobEntity job, String message) {
        job.errorMessage = message;
        finish(job, JobState.FAILED, null);
    }

    // ------------------------------------------------------------------ CANCEL / RESUME

    public void handleCancel(String jobId) {
        tx.executeWithoutResult(s -> {
            ReproductionJobEntity job = jobs.findById(jobId).orElseThrow(() -> new PoisonMessageException("CANCEL for unknown job " + jobId));
            if (job.status.terminal()) { log.info("CANCEL ignored, job already {}", job.status); return; }
            machine.transition(job, JobState.CANCELLED, Instant.now());
            jobs.save(job);
            for (WorkerTaskEntity t : tasks.findByJobIdAndStatusIn(jobId, OUTSTANDING)) { t.status = WorkerTaskEntity.Status.DEAD; t.updatedAt = Instant.now(); tasks.save(t); }
            AfterCommit.run(() -> progress.setCancelled(jobId, true));
            progress.set(jobId, Map.of("state", "CANCELLED"));
        });
    }

    public void handleResume(String jobId, String correlationId) {
        tx.executeWithoutResult(s -> {
            ReproductionJobEntity job = jobs.findById(jobId).orElseThrow(() -> new PoisonMessageException("RESUME for unknown job " + jobId));
            if (!job.status.terminal() || job.status == JobState.COMPLETED) { log.info("RESUME ignored, job is {}", job.status); return; }
            JobContext ctx = contexts.build(jobId, Map.of());
            contexts.cache(ctx);
            progress.setCancelled(jobId, false);
            machine.transition(job, JobState.SEARCHING, Instant.now());
            job.errorMessage = null;
            job.deadlineAt = Instant.now().plus(ctx.config().maxExecutionTime());
            jobs.save(job);
            attempts.save(new JobAttemptEntity(jobId, nextAttemptNo(jobId), "RESUME", Instant.now()));
            List<WorkerTaskEntity> existing = tasks.findByJobId(jobId);
            if (existing.isEmpty()) createAndPublish(job, List.of(), 1, correlationId);
            for (WorkerTaskEntity t : existing)
                if (t.status == WorkerTaskEntity.Status.DEAD || t.status == WorkerTaskEntity.Status.FAILED
                        || (t.status == WorkerTaskEntity.Status.COMPLETED && isPartial(t))) {
                    t.attempt++; t.status = WorkerTaskEntity.Status.PENDING; t.resultJson = null;
                    publisher.publish(t, job, correlationId);
                    tasks.save(t);
                }
            progress.set(jobId, Map.of("state", "SEARCHING"));
        });
    }

    private boolean isPartial(WorkerTaskEntity t) {
        return t.resultJson != null && read(t.resultJson, TaskResult.class).outcome() == TaskResult.Outcome.PARTIAL_BUDGET;
    }

    private void refreshProgress(ReproductionJobEntity job) {
        List<WorkerTaskEntity> all = tasks.findByJobId(job.id);
        long done = all.stream().filter(t -> t.status == WorkerTaskEntity.Status.COMPLETED).count();
        progress.set(job.id, Map.of("state", job.status.name(), "tasksTotal", Long.toString(all.size()), "tasksCompleted", Long.toString(done)));
    }

    // ------------------------------------------------------------------ helpers

    private int nextAttemptNo(String jobId) { return attempts.findFirstByJobIdOrderByAttemptNoDesc(jobId).map(a -> a.attemptNo + 1).orElse(1); }

    private JobOptions options(ReproductionJobEntity job) { return read(job.optionsJson, JobOptions.class); }

    private Map<String, Object> parse(String jsonText) {
        try { return mapper.readValue(jsonText, new TypeReference<Map<String, Object>>() {}); }
        catch (Exception e) { throw new PoisonMessageException("unreadable job input: " + e.getMessage()); }
    }

    private String json(Object o) {
        try { return mapper.writeValueAsString(o); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private <T> T read(String s, Class<T> c) {
        try { return mapper.readValue(s, c); } catch (Exception e) { throw new IllegalStateException("corrupt stored JSON", e); }
    }

}
