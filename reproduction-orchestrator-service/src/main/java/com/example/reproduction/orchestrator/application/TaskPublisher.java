package com.example.reproduction.orchestrator.application;

import com.example.reproduction.messaging.CandidateTask;
import com.example.reproduction.messaging.Topics;
import com.example.reproduction.orchestrator.config.OrchestratorProperties;
import com.example.reproduction.persistence.entity.ReproductionJobEntity;
import com.example.reproduction.persistence.entity.WorkerTaskEntity;
import com.example.reproduction.util.Hashing;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Turns a worker_task row into a Kafka message. Key = taskId so tasks of one job spread over partitions. */
@Component
public class TaskPublisher {
    private static final Logger log = LoggerFactory.getLogger(TaskPublisher.class);
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper mapper;
    private final OrchestratorProperties props;

    public TaskPublisher(KafkaTemplate<String, Object> kafka, ObjectMapper mapper, OrchestratorProperties props) {
        this.kafka = kafka; this.mapper = mapper; this.props = props;
    }

    /** Sets the lease and schedules the send for after the surrounding transaction commits. */
    public void publish(WorkerTaskEntity t, ReproductionJobEntity job, String correlationId) {
        Instant now = Instant.now();
        t.leaseExpiresAt = now.plus(props.taskTimeout());
        t.updatedAt = now;
        List<String> bans = bans(t);
        String hash = Hashing.sha256Hex(t.jobId + "|" + String.join(",", bans));
        CandidateTask msg = new CandidateTask(t.jobId, t.taskId, "cand-" + hash.substring(0, 12), hash, t.attempt, now, correlationId,
                CandidateTask.Type.valueOf(t.type), bans, job.deadlineAt);
        String topic = t.attempt > 1 ? Topics.CANDIDATES_RETRY : Topics.CANDIDATES;
        AfterCommit.run(() -> kafka.send(topic, t.taskId, msg).whenComplete((r, ex) -> {
            if (ex != null) log.error("task {} not published ({}); sweeper will re-publish after lease expiry", t.taskId, ex.toString());
        }));
    }

    public List<String> bans(WorkerTaskEntity t) {
        try { return mapper.readValue(t.bannedJson, new TypeReference<List<String>>() {}); }
        catch (Exception e) { throw new IllegalStateException("corrupt banned_json for task " + t.taskId, e); }
    }
}
