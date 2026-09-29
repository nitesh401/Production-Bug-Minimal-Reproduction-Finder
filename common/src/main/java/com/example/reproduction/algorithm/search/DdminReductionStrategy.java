package com.example.reproduction.algorithm.search;

import java.util.ArrayList;
import java.util.BitSet;

/** Field-level ddmin: every field is an independent unit. Ignores dependencies (may find spurious failures). */
public final class DdminReductionStrategy extends AbstractReductionStrategy {
    @Override public String name() { return "DDMIN"; }

    @Override protected BitSet allowedStart(ReductionContext ctx) { return minus(ctx.startFields(), ctx.bannedFields()); }

    @Override
    protected ReductionOutcome doReduce(ReductionContext ctx, BitSet allowed) {
        UnitSpace space = UnitSpace.singletons(allowed, ctx.analysis());
        return runDdmin(ctx, space, space.allUnits(), "field", null, u -> u, new ArrayList<>());
    }
}
