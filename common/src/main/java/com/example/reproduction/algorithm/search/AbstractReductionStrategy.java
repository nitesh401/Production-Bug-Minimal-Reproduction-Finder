package com.example.reproduction.algorithm.search;

import com.example.reproduction.algorithm.ddmin.Ddmin;
import com.example.reproduction.domain.EvaluationResult;
import com.example.reproduction.domain.ReductionStep;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.UnaryOperator;

/** Template: verify the (allowed) start set reproduces, then delegate to the concrete reduction. */
abstract class AbstractReductionStrategy implements ReductionStrategy {

    @Override
    public final ReductionOutcome reduce(ReductionContext ctx) {
        BitSet allowed = allowedStart(ctx);
        EvaluationResult r = ctx.evaluator().evaluate(allowed);
        if (!r.reproduces()) return ReductionOutcome.notReproducing(allowed, r.status());
        return doReduce(ctx, allowed);
    }

    protected abstract BitSet allowedStart(ReductionContext ctx);

    protected abstract ReductionOutcome doReduce(ReductionContext ctx, BitSet allowed);

    /** Runs ddmin over a unit space and converts the result to field-level steps and proof. */
    protected static ReductionOutcome runDdmin(ReductionContext ctx, UnitSpace space, BitSet startUnits, String phase,
                                               int[] rank, UnaryOperator<BitSet> normalizer, List<ReductionStep> steps) {
        EvaluatorUnitTester tester = new EvaluatorUnitTester(space, ctx.evaluator(), ctx.executor());
        int[] previous = {space.expand(startUnits).cardinality()};
        Ddmin.StepListener recorder = (action, before, after, gran, units) -> {
            BitSet f = space.expand(units);
            ReductionStep s = new ReductionStep(steps.size() + 1, phase, action, previous[0], f.cardinality(), gran,
                    ctx.universe().candidateOf(f).hash(), ctx.universe().pathsOf(f));
            previous[0] = f.cardinality();
            steps.add(s);
            ctx.listener().onStep(s);
        };
        Ddmin.Result res = new Ddmin().run(startUnits, rank, normalizer, tester, recorder);
        List<ReductionOutcome.ProofLine> proof = new ArrayList<>();
        for (Ddmin.ProofEntry p : res.proof())
            proof.add(new ReductionOutcome.ProofLine(ctx.universe().pathsOf(space.fieldsOf(p.unit())), p.verdict().name()));
        return new ReductionOutcome(space.expand(res.units()), com.example.reproduction.domain.EvaluationStatus.REPRODUCES_BUG,
                res.provenMinimal(), steps, proof, res.stopReason(), res.iterations());
    }

    protected static BitSet minus(BitSet a, BitSet b) {
        BitSet r = (BitSet) a.clone();
        if (b != null) r.andNot(b);
        return r;
    }
}
