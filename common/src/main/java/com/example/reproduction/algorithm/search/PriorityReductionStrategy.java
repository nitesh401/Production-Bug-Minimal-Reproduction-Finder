package com.example.reproduction.algorithm.search;

/**
 * Dependency-aware reduction with a priority order: units with low historical keep-likelihood, low criticality
 * and large payload are placed in the first partitions, so the biggest / least likely necessary chunks are
 * tried for removal first.
 */
public final class PriorityReductionStrategy extends DependencyAwareReductionStrategy {
    @Override public String name() { return "PRIORITY"; }

    @Override
    protected int[] rank(ReductionContext ctx, UnitSpace space) {
        return space.priorityRank(ctx.universe(), ctx.keepHints() == null ? java.util.Map.of() : ctx.keepHints());
    }
}
