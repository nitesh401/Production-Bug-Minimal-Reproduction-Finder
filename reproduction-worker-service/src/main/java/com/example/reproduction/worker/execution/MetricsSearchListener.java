package com.example.reproduction.worker.execution;

import com.example.reproduction.algorithm.search.SearchListener;
import com.example.reproduction.domain.EvaluationResult;
import com.example.reproduction.domain.FieldUniverse;
import com.example.reproduction.domain.ReductionStep;
import com.example.reproduction.messaging.CandidateTask;
import com.example.reproduction.messaging.EvaluationEvent;
import com.example.reproduction.messaging.Topics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.BitSet;

/** Observer: turns evaluator callbacks into Kafka events and Micrometer metrics. */
final class MetricsSearchListener implements SearchListener {
    private static final Logger log = LoggerFactory.getLogger(MetricsSearchListener.class);
    private final KafkaTemplate<String, Object> kafka;
    private final MeterRegistry meters;
    private final CandidateTask task;
    private final FieldUniverse universe;

    MetricsSearchListener(KafkaTemplate<String, Object> kafka, MeterRegistry meters, CandidateTask task, FieldUniverse universe) {
        this.kafka = kafka; this.meters = meters; this.task = task; this.universe = universe;
    }

    @Override
    public void onEvaluation(BitSet fields, String candidateHash, EvaluationResult r) {
        switch (r.source()) {
            case CACHE -> meters.counter("candidate.cache.hits").increment();
            case INFERRED_BY_DOMINANCE -> { meters.counter("candidate.dominance.inferred").increment(); return; } // not an evaluation: no event
            case EVALUATED -> {
                meters.counter("candidate.cache.misses").increment();
                meters.counter("candidate.evaluations", "status", r.status().name()).increment();
                Timer.builder("evaluation.time").register(meters).record(Duration.ofMillis(Math.max(0, r.durationMillis())));
            }
        }
        EvaluationEvent e = new EvaluationEvent(task.jobId(), task.taskId(), "cand-" + candidateHash.substring(0, 12), candidateHash,
                task.attempt(), Instant.now(), task.correlationId(), r.status(), r.attempts(), r.reproductions(),
                r.reproductionRate(), r.durationMillis(), r.source().name(), universe.pathsOf(fields));
        // fire-and-forget: evaluation events are audit/progress data, the TaskResult is the source of truth
        kafka.send(Topics.EVALUATIONS, task.jobId(), e).whenComplete((res, ex) -> {
            if (ex != null) { meters.counter("evaluation.event.publish.failures").increment(); log.warn("evaluation event not published: {}", ex.toString()); }
        });
    }

    @Override
    public void onStep(ReductionStep step) { meters.counter("reduction.iterations").increment(); }
}
