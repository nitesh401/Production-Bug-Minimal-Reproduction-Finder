package com.example.reproduction.domain;

public record CandidateScore(double total, int fieldCount, int payloadBytes, int dependencyEdges,
                             double confidence, double evaluationCostMillis) {}
