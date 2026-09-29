package com.example.reproduction.algorithm;

import com.example.reproduction.algorithm.search.DominanceIndex;
import com.example.reproduction.domain.EvaluationStatus;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.*;

class DominanceIndexTest {
    static BitSet bits(int... i) { BitSet b = new BitSet(); for (int x : i) b.set(x); return b; }

    @Test
    void supersetOfReproducingReproduces_subsetOfNonReproducingDoesNot() {
        DominanceIndex d = new DominanceIndex();
        d.record(bits(1, 2), EvaluationStatus.REPRODUCES_BUG);
        d.record(bits(3, 4, 5), EvaluationStatus.DOES_NOT_REPRODUCE);
        assertEquals(EvaluationStatus.REPRODUCES_BUG, d.infer(bits(1, 2, 9)));
        assertEquals(EvaluationStatus.DOES_NOT_REPRODUCE, d.infer(bits(3, 5)));
        assertNull(d.infer(bits(1, 9)));
        assertNull(d.infer(bits(3, 6)));
    }

    @Test
    void keepsAntichainsAndIgnoresInconclusive() {
        DominanceIndex d = new DominanceIndex();
        d.record(bits(1, 2, 3), EvaluationStatus.REPRODUCES_BUG);
        d.record(bits(1, 2), EvaluationStatus.REPRODUCES_BUG); // replaces the superset
        d.record(bits(1, 2, 3, 4), EvaluationStatus.REPRODUCES_BUG); // implied, ignored
        assertEquals(1, d.size());
        d.record(bits(7), EvaluationStatus.TIMEOUT);
        d.record(bits(8), EvaluationStatus.INCONCLUSIVE);
        assertEquals(1, d.size());
        assertNull(d.infer(bits(7)));
    }
}
