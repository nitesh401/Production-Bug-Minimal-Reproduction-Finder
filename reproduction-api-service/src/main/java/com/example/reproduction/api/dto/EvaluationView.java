package com.example.reproduction.api.dto;

public record EvaluationView(String candidateHash, String taskId, int attempt, String status, int attemptsRun,
                             int reproductions, double reproductionRate, long durationMs, String source) {}
