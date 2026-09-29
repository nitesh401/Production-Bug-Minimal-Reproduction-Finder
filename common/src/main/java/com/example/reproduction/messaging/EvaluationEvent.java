package com.example.reproduction.messaging;

import com.example.reproduction.domain.EvaluationStatus;

import java.time.Instant;
import java.util.List;

public record EvaluationEvent(String jobId, String taskId, String candidateId, String candidateHash, int attempt,
                              Instant timestamp, String correlationId, EvaluationStatus status, int attemptsRun,
                              int reproductions, double reproductionRate, long durationMillis, String source,
                              List<String> fieldPaths) {}
