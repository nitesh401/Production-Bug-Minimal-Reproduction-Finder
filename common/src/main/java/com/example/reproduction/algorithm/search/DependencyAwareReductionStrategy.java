package com.example.reproduction.algorithm.search;

import java.util.ArrayList;
import java.util.BitSet;

/**
 * Reduces over SCC units. Candidates that would violate a dependency are repaired away by
 * {@link UnitSpace#normalize} (never evaluated), so the search only visits dependency-consistent inputs.
 */
public class DependencyAwareReductionStrategy extends AbstractReductionStrategy {
    @Override public String name() { return "DEPENDENCY_AWARE"; }

    protected UnitSpace space(ReductionContext ctx) {
        return UnitSpace.scc(ctx.analysis(), ctx.startFields(), ctx.bannedFields());
    }

    @Override protected BitSet allowedStart(ReductionContext ctx) { return space(ctx).expand(space(ctx).allUnits()); }

    protected int[] rank(ReductionContext ctx, UnitSpace space) { return null; }

    @Override
    protected ReductionOutcome doReduce(ReductionContext ctx, BitSet allowed) {
        UnitSpace space = space(ctx);
        return runDdmin(ctx, space, space.allUnits(), "scc", rank(ctx, space), space::normalize, new ArrayList<>());
    }
}
