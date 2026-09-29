package com.example.reproduction.algorithm.search;

import com.example.reproduction.domain.ReductionStep;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * 1. group level: ddmin over independent weak components (cheap, removes whole irrelevant clusters);
 * 2. dependency level: priority-ordered ddmin over SCC units inside the survivors;
 * 3. randomised fallback: seeded restarts with shuffled unit orders (escapes ordering-induced local minima).
 * The smallest result wins; the final result always carries the SCC-level 1-minimality proof.
 */
public final class HybridReductionStrategy extends AbstractReductionStrategy {
    @Override public String name() { return "HYBRID"; }

    @Override
    protected BitSet allowedStart(ReductionContext ctx) {
        UnitSpace s = UnitSpace.scc(ctx.analysis(), ctx.startFields(), ctx.bannedFields());
        return s.expand(s.allUnits());
    }

    @Override
    protected ReductionOutcome doReduce(ReductionContext ctx, BitSet allowed) {
        List<ReductionStep> steps = new ArrayList<>();
        BitSet survivors = allowed;

        UnitSpace groups = UnitSpace.weakComponents(ctx.analysis(), allowed);
        if (groups.size() > 1) {
            ReductionOutcome g = runDdmin(ctx, groups, groups.allUnits(), "group", null, u -> u, steps);
            survivors = g.fields();
            if (g.stopReason() != com.example.reproduction.domain.StopReason.COMPLETED)
                return new ReductionOutcome(survivors, g.startStatus(), false, steps, List.of(), g.stopReason(), g.iterations());
        }

        UnitSpace scc = UnitSpace.scc(ctx.analysis(), survivors, ctx.bannedFields());
        int[] rank = scc.priorityRank(ctx.universe(), ctx.keepHints() == null ? java.util.Map.of() : ctx.keepHints());
        ReductionOutcome best = runDdmin(ctx, scc, scc.allUnits(), "scc", rank, scc::normalize, steps);

        for (int i = 1; i <= ctx.config().randomRestarts() && best.stopReason() == com.example.reproduction.domain.StopReason.COMPLETED; i++) {
            UnitSpace full = UnitSpace.scc(ctx.analysis(), allowed, ctx.bannedFields());
            ReductionOutcome alt = runDdmin(ctx, full, full.allUnits(), "random-restart-" + i,
                    full.randomRank(ctx.config().randomSeed() + i), full::normalize, steps);
            if (alt.fields().cardinality() < best.fields().cardinality()) best = alt;
        }
        return new ReductionOutcome(best.fields(), best.startStatus(), best.provenMinimal(), steps, best.proof(), best.stopReason(), best.iterations());
    }
}
