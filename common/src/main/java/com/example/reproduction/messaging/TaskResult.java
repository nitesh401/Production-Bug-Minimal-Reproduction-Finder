package com.example.reproduction.messaging;

import com.example.reproduction.algorithm.search.ReductionOutcome;
import com.example.reproduction.domain.ReductionStep;
import com.example.reproduction.domain.StopReason;

import java.time.Instant;
import java.util.List;

public record TaskResult(String jobId, String taskId, String candidateId, String candidateHash, int attempt,
                         Instant timestamp, String correlationId, Outcome outcome, List<String> minimalFields,
                         boolean provenMinimal, double reproductionRate, List<ReductionStep> steps,
                         List<ReductionOutcome.ProofLine> proof, StopReason stopReason, String detail,
                         long evaluations, long cacheHits) {
    public enum Outcome { SUCCESS, START_NOT_REPRODUCING, START_INCONCLUSIVE, PARTIAL_BUDGET, CANCELLED, FAILED }
}
