package com.example.reproduction.algorithm.ddmin;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/** Tests candidate sets of "units" (fields, SCCs or components). Implementations may parallelise batches. */
public interface UnitTester {

    Verdict test(BitSet units, String purpose);

    /** Result of a batch: index of the LOWEST-index reproducing candidate (-1 if none) + verdicts observed. */
    record BatchOutcome(int firstReproducing, Verdict[] verdicts) {}

    /** Default: sequential with early exit. Parallel testers must still return the lowest reproducing index. */
    default BatchOutcome testFirst(List<BitSet> candidates, String purpose) {
        Verdict[] vs = new Verdict[candidates.size()];
        for (int i = 0; i < vs.length; i++) {
            vs[i] = test(candidates.get(i), purpose);
            if (vs[i] == Verdict.REPRODUCES) return new BatchOutcome(i, vs);
        }
        return new BatchOutcome(-1, vs);
    }

    /** Evaluate all candidates (used for the minimality proof, where every verdict matters). */
    default Verdict[] testAll(List<BitSet> candidates, String purpose) {
        List<Verdict> l = new ArrayList<>();
        for (BitSet c : candidates) l.add(test(c, purpose));
        return l.toArray(new Verdict[0]);
    }
}
