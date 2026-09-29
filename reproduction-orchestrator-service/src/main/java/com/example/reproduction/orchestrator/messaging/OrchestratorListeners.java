package com.example.reproduction.orchestrator.messaging;

import com.example.reproduction.messaging.*;
import com.example.reproduction.orchestrator.application.EvaluationRecorder;
import com.example.reproduction.orchestrator.application.JobOrchestrator;
import com.example.reproduction.platform.kafka.PoisonMessageException;
import com.example.reproduction.platform.logging.Correlation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka entry points. They only validate, set the logging context and delegate; failures propagate to the container
 * error handler (bounded retry, then DLQ). Optimistic-lock conflicts are therefore simply retried.
 */
@Component
public class OrchestratorListeners {
    private static final Logger log = LoggerFactory.getLogger(OrchestratorListeners.class);
    private final JobOrchestrator orchestrator;
    private final EvaluationRecorder evaluations;

    public OrchestratorListeners(JobOrchestrator orchestrator, EvaluationRecorder evaluations) {
        this.orchestrator = orchestrator; this.evaluations = evaluations;
    }

    @KafkaListener(topics = Topics.JOBS, groupId = "orchestrator-jobs",
            properties = "spring.json.value.default.type=com.example.reproduction.messaging.JobCommand")
    public void onCommand(JobCommand c) {
        if (c == null || c.type() == null || c.jobId() == null) throw new PoisonMessageException("malformed JobCommand: " + c);
        try (Correlation ctx = Correlation.of(c.correlationId(), c.jobId(), null, null)) {
            log.info("job command {}", c.type());
            switch (c.type()) {
                case SUBMIT -> orchestrator.handleSubmit(c.jobId(), c.correlationId());
                case CANCEL -> orchestrator.handleCancel(c.jobId());
                case RESUME -> orchestrator.handleResume(c.jobId(), c.correlationId());
            }
        }
    }

    @KafkaListener(topics = Topics.RESULTS, groupId = "orchestrator-results",
            properties = "spring.json.value.default.type=com.example.reproduction.messaging.TaskResult")
    public void onResult(TaskResult r) {
        if (r == null || r.jobId() == null || r.taskId() == null || r.outcome() == null) throw new PoisonMessageException("malformed TaskResult: " + r);
        try (Correlation ctx = Correlation.of(r.correlationId(), r.jobId(), r.taskId(), r.candidateId())) {
            log.info("task result outcome={} attempt={}", r.outcome(), r.attempt());
            orchestrator.handleResult(r);
        }
    }

    @KafkaListener(topics = Topics.EVALUATIONS, groupId = "orchestrator-evaluations", concurrency = "3",
            properties = "spring.json.value.default.type=com.example.reproduction.messaging.EvaluationEvent")
    public void onEvaluation(EvaluationEvent e) {
        if (e == null || e.jobId() == null || e.candidateHash() == null || e.taskId() == null || e.fieldPaths() == null)
            throw new PoisonMessageException("malformed EvaluationEvent: " + e);
        try (Correlation ctx = Correlation.of(e.correlationId(), e.jobId(), e.taskId(), e.candidateId())) {
            evaluations.record(e);
        }
    }
}
