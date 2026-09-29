package com.example.reproduction.domain;

/** Weights of the (lower-is-better) candidate score. */
public record ScoringWeights(double fieldCount, double payloadSize, double dependencyComplexity,
                             double evaluationCost, double confidencePenalty) {
    public static ScoringWeights defaults() { return new ScoringWeights(10.0, 0.01, 1.0, 0.001, 100.0); }
}
