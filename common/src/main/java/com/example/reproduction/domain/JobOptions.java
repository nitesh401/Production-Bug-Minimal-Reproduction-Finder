package com.example.reproduction.domain;

import java.time.Duration;

/** Optional per-job overrides; null means "use the default". */
public record JobOptions(StrategyType strategy, Integer evaluationAttempts, Double minimumReproductionRate,
                         Integer maxEvaluations, Long maxExecutionSeconds, Integer maxConcurrentEvaluations,
                         Integer maxSolutions, Integer randomRestarts, Boolean assumeMonotonic) {

    public SearchConfig toSearchConfig() {
        SearchConfig.Builder b = SearchConfig.builder();
        if (strategy != null) b.strategy(strategy);
        if (evaluationAttempts != null) b.evaluationAttempts(evaluationAttempts);
        if (minimumReproductionRate != null) b.minimumReproductionRate(minimumReproductionRate);
        if (maxEvaluations != null) b.maxEvaluations(maxEvaluations);
        if (maxExecutionSeconds != null) b.maxExecutionTime(Duration.ofSeconds(maxExecutionSeconds));
        if (maxConcurrentEvaluations != null) b.maxConcurrentEvaluations(maxConcurrentEvaluations);
        if (maxSolutions != null) b.maxSolutions(maxSolutions);
        if (randomRestarts != null) b.randomRestarts(randomRestarts);
        if (assumeMonotonic != null) b.assumeMonotonic(assumeMonotonic);
        return b.build();
    }
}
