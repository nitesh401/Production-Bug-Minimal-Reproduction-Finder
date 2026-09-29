package com.example.reproduction.messaging;

import java.time.Instant;
import java.util.List;

/**
 * A unit of distributed work: "reduce the job's input while excluding {@code bannedFields}".
 * candidateId/candidateHash identify the START candidate (full input minus bans), which is the task's natural
 * idempotency anchor together with (jobId, taskId, attempt).
 */
public record CandidateTask(String jobId, String taskId, String candidateId, String candidateHash, int attempt,
                            Instant timestamp, String correlationId, Type type, List<String> bannedFields,
                            Instant deadline) {
    public enum Type { PRIMARY, ALTERNATIVE }
}
