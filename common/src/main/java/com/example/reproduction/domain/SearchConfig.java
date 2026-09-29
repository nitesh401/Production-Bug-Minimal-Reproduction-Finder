package com.example.reproduction.domain;

import java.time.Duration;

/** Immutable search configuration: strategy, budgets, flakiness policy, scoring. */
public record SearchConfig(StrategyType strategy, int evaluationAttempts, double minimumReproductionRate,
                           int maxEvaluations, Duration maxExecutionTime, int maxConcurrentEvaluations,
                           int maxInfraRetries, long retryBackoffMillis, int maxSolutions,
                           boolean assumeMonotonic, int randomRestarts, long randomSeed,
                           ScoringWeights weights) {

    public SearchConfig {
        if (evaluationAttempts < 1) throw new IllegalArgumentException("evaluationAttempts must be >= 1");
        if (minimumReproductionRate <= 0 || minimumReproductionRate > 1)
            throw new IllegalArgumentException("minimumReproductionRate must be in (0,1]");
        if (maxEvaluations < 1) throw new IllegalArgumentException("maxEvaluations must be >= 1");
        if (maxConcurrentEvaluations < 1) throw new IllegalArgumentException("maxConcurrentEvaluations must be >= 1");
        if (maxSolutions < 1) throw new IllegalArgumentException("maxSolutions must be >= 1");
    }

    public static SearchConfig defaults() { return builder().build(); }

    public static Builder builder() { return new Builder(); }

    public Builder toBuilder() {
        return new Builder().strategy(strategy).evaluationAttempts(evaluationAttempts)
                .minimumReproductionRate(minimumReproductionRate).maxEvaluations(maxEvaluations)
                .maxExecutionTime(maxExecutionTime).maxConcurrentEvaluations(maxConcurrentEvaluations)
                .maxInfraRetries(maxInfraRetries).retryBackoffMillis(retryBackoffMillis).maxSolutions(maxSolutions)
                .assumeMonotonic(assumeMonotonic).randomRestarts(randomRestarts).randomSeed(randomSeed)
                .weights(weights);
    }

    public static final class Builder {
        private StrategyType strategy = StrategyType.HYBRID;
        private int evaluationAttempts = 1;
        private double minimumReproductionRate = 1.0;
        private int maxEvaluations = 5000;
        private Duration maxExecutionTime = Duration.ofMinutes(5);
        private int maxConcurrentEvaluations = 4;
        private int maxInfraRetries = 3;
        private long retryBackoffMillis = 50;
        private int maxSolutions = 5;
        private boolean assumeMonotonic = true;
        private int randomRestarts = 0;
        private long randomSeed = 42;
        private ScoringWeights weights = ScoringWeights.defaults();

        public Builder strategy(StrategyType v) { strategy = v; return this; }
        public Builder evaluationAttempts(int v) { evaluationAttempts = v; return this; }
        public Builder minimumReproductionRate(double v) { minimumReproductionRate = v; return this; }
        public Builder maxEvaluations(int v) { maxEvaluations = v; return this; }
        public Builder maxExecutionTime(Duration v) { maxExecutionTime = v; return this; }
        public Builder maxConcurrentEvaluations(int v) { maxConcurrentEvaluations = v; return this; }
        public Builder maxInfraRetries(int v) { maxInfraRetries = v; return this; }
        public Builder retryBackoffMillis(long v) { retryBackoffMillis = v; return this; }
        public Builder maxSolutions(int v) { maxSolutions = v; return this; }
        public Builder assumeMonotonic(boolean v) { assumeMonotonic = v; return this; }
        public Builder randomRestarts(int v) { randomRestarts = v; return this; }
        public Builder randomSeed(long v) { randomSeed = v; return this; }
        public Builder weights(ScoringWeights v) { weights = v; return this; }

        public SearchConfig build() {
            return new SearchConfig(strategy, evaluationAttempts, minimumReproductionRate, maxEvaluations,
                    maxExecutionTime, maxConcurrentEvaluations, maxInfraRetries, retryBackoffMillis, maxSolutions,
                    assumeMonotonic, randomRestarts, randomSeed, weights);
        }
    }
}
