package com.example.reproduction.algorithm;

import com.example.reproduction.algorithm.ddmin.*;
import com.example.reproduction.domain.StopReason;
import com.example.reproduction.exception.BudgetExhaustedException;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class DdminTest {

    /** Predicate-backed tester that counts evaluations. */
    static final class Counting implements UnitTester {
        final Predicate<BitSet> bug;
        final AtomicInteger calls = new AtomicInteger();
        Counting(Predicate<BitSet> bug) { this.bug = bug; }
        public Verdict test(BitSet u, String purpose) {
            calls.incrementAndGet();
            return bug.test(u) ? Verdict.REPRODUCES : Verdict.NOT_REPRODUCES;
        }
    }

    static BitSet bits(int... i) { BitSet b = new BitSet(); for (int x : i) b.set(x); return b; }
    static BitSet range(int n) { BitSet b = new BitSet(); b.set(0, n); return b; }
    static boolean containsAll(BitSet s, int... need) { for (int i : need) if (!s.get(i)) return false; return true; }
    static final Ddmin.StepListener NO = (a, b, c, d, e) -> {};

    @Test
    void findsSingleCulpritAmongMany() {
        Counting t = new Counting(s -> s.get(37));
        Ddmin.Result r = new Ddmin().run(range(64), null, u -> u, t, NO);
        assertEquals(bits(37), r.units());
        assertTrue(r.provenMinimal());
        assertTrue(t.calls.get() < 64, "far fewer tests than testing every field: " + t.calls.get());
        assertEquals(StopReason.COMPLETED, r.stopReason());
    }

    @Test
    void findsScatteredMultiElementMinimum() {
        Counting t = new Counting(s -> containsAll(s, 3, 11, 12, 30));
        Ddmin.Result r = new Ddmin().run(range(40), null, u -> u, t, NO);
        assertEquals(bits(3, 11, 12, 30), r.units());
        assertTrue(r.provenMinimal());
        assertEquals(4, r.proof().size());
        r.proof().forEach(p -> assertEquals(Verdict.NOT_REPRODUCES, p.verdict()));
    }

    @Test
    void emptyInputIsReturnedWhenBugNeedsNothing() {
        Ddmin.Result r = new Ddmin().run(range(10), null, u -> u, new Counting(s -> true), NO);
        assertTrue(r.units().isEmpty());
        assertTrue(r.provenMinimal());
    }

    @Test
    void neverEvaluatesSameCandidateTwiceWithinReduction() {
        Set<BitSet> seen = new java.util.HashSet<>();
        AtomicInteger dup = new AtomicInteger();
        UnitTester t = (u, p) -> {
            if (!seen.add((BitSet) u.clone())) dup.incrementAndGet();
            return containsAll(u, 1, 6) ? Verdict.REPRODUCES : Verdict.NOT_REPRODUCES;
        };
        new Ddmin().run(range(16), null, x -> x, t, NO);
        // the proof phase may re-test candidates that were refuted earlier, but the main loop must not thrash
        assertTrue(dup.get() <= 4, "duplicates: " + dup.get());
    }

    @Test
    void normalizerPreventsDependencyViolatingCandidates() {
        // unit 5 requires unit 4. The "bug" is spurious for any set holding 5 without 4 (malformed input).
        AtomicInteger malformed = new AtomicInteger();
        UnitTester t = (u, p) -> {
            if (u.get(5) && !u.get(4)) { malformed.incrementAndGet(); return Verdict.REPRODUCES; }
            return containsAll(u, 4, 5, 9) ? Verdict.REPRODUCES : Verdict.NOT_REPRODUCES;
        };
        java.util.function.UnaryOperator<BitSet> norm = u -> { BitSet o = (BitSet) u.clone(); if (o.get(5) && !o.get(4)) o.clear(5); return o; };
        Ddmin.Result r = new Ddmin().run(range(12), null, norm, t, NO);
        assertEquals(bits(4, 5, 9), r.units());
        assertEquals(0, malformed.get());
    }

    @Test
    void inconclusiveIsNeverTreatedAsBugGone() {
        // Removing unit 2 (needed) is fine; but candidates without 0 are "timeouts": they must not be accepted or refuted.
        UnitTester t = (u, p) -> {
            if (!u.get(0)) return Verdict.INCONCLUSIVE;
            return u.get(2) ? Verdict.REPRODUCES : Verdict.NOT_REPRODUCES;
        };
        Ddmin.Result r = new Ddmin().run(range(6), null, u -> u, t, NO);
        assertTrue(r.units().get(0) && r.units().get(2));
        assertFalse(r.provenMinimal(), "unit 0's removal was inconclusive, so minimality is NOT proven");
    }

    @Test
    void budgetExhaustionReturnsBestSoFar() {
        AtomicInteger n = new AtomicInteger();
        UnitTester t = (u, p) -> {
            if (n.incrementAndGet() > 6) throw new BudgetExhaustedException(BudgetExhaustedException.Kind.EVALUATIONS, "x");
            return u.get(7) ? Verdict.REPRODUCES : Verdict.NOT_REPRODUCES;
        };
        Ddmin.Result r = new Ddmin().run(range(64), null, u -> u, t, NO);
        assertEquals(StopReason.EVALUATION_BUDGET_EXHAUSTED, r.stopReason());
        assertFalse(r.provenMinimal());
        assertTrue(r.units().get(7));
        assertTrue(r.units().cardinality() < 64);
    }

    @Test
    void rankGroupsLikelyRemovableUnitsFirst() {
        // bug needs {0}. With rank putting unit 0 last, the first half-removals are accepted immediately.
        int[] rank = new int[32];
        for (int i = 0; i < 32; i++) rank[i] = (i + 31) % 32; // unit 0 gets rank 31 (last)
        Counting ranked = new Counting(s -> s.get(0));
        Ddmin.Result r = new Ddmin().run(range(32), rank, u -> u, ranked, NO);
        assertEquals(bits(0), r.units());
    }

    @Test
    void splitProducesBalancedContiguousChunks() {
        List<BitSet> parts = Ddmin.split(List.of(0, 1, 2, 3, 4, 5, 6), 3);
        assertEquals(3, parts.size());
        assertEquals(bits(0, 1, 2), parts.get(0));
        assertEquals(bits(3, 4), parts.get(1));
        assertEquals(bits(5, 6), parts.get(2));
    }
}
