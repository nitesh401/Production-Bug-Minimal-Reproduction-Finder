package com.example.reproduction.worker.messaging;

import com.example.reproduction.messaging.CandidateTask;
import com.example.reproduction.messaging.TaskResult;
import com.example.reproduction.messaging.Topics;
import com.example.reproduction.platform.kafka.PoisonMessageException;
import com.example.reproduction.platform.logging.Correlation;
import com.example.reproduction.worker.config.WorkerProperties;
import com.example.reproduction.worker.execution.SearchTaskExecutor;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Consumes search tasks from reproduction.candidates and reproduction.candidates.retry.
 * Delivery is at-least-once; correctness comes from idempotency:
 *   finished task -> re-publish stored result; attempt locked by someone else -> skip; otherwise execute.
 * Any unexpected exception propagates to the container error handler (retry with back-off, then DLQ).
 */
@Component
public class CandidateTaskListener {
    private static final Logger log = LoggerFactory.getLogger(CandidateTaskListener.class);
    private final SearchTaskExecutor executor;
    private final TaskDeduplicator dedup;
    private final KafkaTemplate<String, Object> kafka;
    private final MeterRegistry meters;
    private final WorkerProperties props;

    public CandidateTaskListener(SearchTaskExecutor executor, TaskDeduplicator dedup, KafkaTemplate<String, Object> kafka,
                                 MeterRegistry meters, WorkerProperties props) {
        this.executor = executor; this.dedup = dedup; this.kafka = kafka; this.meters = meters; this.props = props;
    }

    @KafkaListener(topics = {Topics.CANDIDATES, Topics.CANDIDATES_RETRY}, groupId = "reproduction-workers",
            concurrency = "${worker.concurrency:2}",
            properties = "spring.json.value.default.type=com.example.reproduction.messaging.CandidateTask")
    public void onTask(CandidateTask task) {
        validate(task);
        try (Correlation c = Correlation.of(task.correlationId(), task.jobId(), task.taskId(), task.candidateId())) {
            var finished = dedup.finished(task.taskId());
            if (finished.isPresent() && finished.get().attempt() >= task.attempt()) {
                meters.counter("task.duplicates").increment();
                log.info("duplicate delivery of finished task: re-publishing stored result");
                publish(finished.get());
                return;
            }
            if (!dedup.tryAcquire(task.taskId(), task.attempt(), props.id(), props.taskLease())) {
                meters.counter("task.duplicates").increment();
                log.info("attempt {} is owned by another worker: skipping", task.attempt());
                return;
            }
            try {
                TaskResult result = executor.execute(task);
                dedup.remember(result);
                publish(result);
                log.info("task finished outcome={} minimal={} evaluations={} cacheHits={}", result.outcome(), result.minimalFields().size(),
                        result.evaluations(), result.cacheHits());
            } catch (PoisonMessageException e) {
                throw e; // never retried
            } catch (RuntimeException e) {
                meters.counter("worker.failures").increment();
                dedup.release(task.taskId(), task.attempt()); // let the retry (this or next attempt) run
                throw e;
            }
        }
    }

    private void publish(TaskResult r) {
        try {
            kafka.send(Topics.RESULTS, r.jobId(), r).get(15, TimeUnit.SECONDS); // durable before we acknowledge the task
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while publishing result", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("result publish failed for task " + r.taskId(), e);
        }
    }

    private static void validate(CandidateTask t) {
        if (t == null || t.jobId() == null || t.taskId() == null || t.candidateHash() == null || t.attempt() < 1)
            throw new PoisonMessageException("malformed CandidateTask: " + t);
    }

}
