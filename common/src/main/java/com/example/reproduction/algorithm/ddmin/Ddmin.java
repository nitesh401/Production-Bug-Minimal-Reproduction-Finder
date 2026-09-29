package com.example.reproduction.algorithm.ddmin;

import com.example.reproduction.domain.StopReason;
import com.example.reproduction.exception.BudgetExhaustedException;
import com.example.reproduction.exception.SearchCancelledException;

import java.util.*;
import java.util.function.UnaryOperator;

/**
 * Improved ddmin (Zeller &amp; Hildebrandt) over abstract "units" (bit indices).
 *
 * <p>Improvements over the textbook algorithm:
 * <ul>
 *   <li>a {@code normalizer} maps every proposed candidate to a dependency-valid one (or shrinks it away), so
 *       invalid candidates are never evaluated (dependency pruning);</li>
 *   <li>a {@code rank} orders units so that contiguous partitions group "likely removable" units first;</li>
 *   <li>a "refuted" set suppresses re-testing candidates already known NOT to reproduce;</li>
 *   <li>batches go through {@link UnitTester#testFirst}, which may evaluate them in parallel while still
 *       choosing the lowest-index reproducing candidate (deterministic result);</li>
 *   <li>the empty candidate is tried first;</li>
 *   <li>the result carries an explicit 1-minimality PROOF: every single-unit removal was tested and did not
 *       reproduce (conclusively). If a proof test reproduces, reduction simply continues.</li>
 * </ul>
 *
 * <p>Complexity (number of tests, n = units): best case when a single small set is failure-inducing about
 * O(log n) rounds; worst case O(n^2) tests (each of up to n granularity levels tests up to 2n candidates and
 * every accepted reduction restarts at n=2). Classic ddmin does NOT guarantee O(log n).
 * Total work is dominated by test cost; caching and dominance pruning in the evaluator avoid repeats.
 */
public final class Ddmin {

    public record ProofEntry(int unit, Verdict verdict) {}

    public record Result(BitSet units, boolean provenMinimal, List<ProofEntry> proof, int iterations, StopReason stopReason) {}

    public interface StepListener {
        void onReduction(String action, int sizeBefore, int sizeAfter, int granularity, BitSet units);
    }

    public Result run(BitSet start, int[] rank, UnaryOperator<BitSet> normalizer, UnitTester tester, StepListener listener) {
        BitSet current = (BitSet) start.clone();
        Set<BitSet> refuted = new HashSet<>();
        int iterations = 0;
        StopReason stop = StopReason.COMPLETED;
        List<ProofEntry> proof = new ArrayList<>();
        boolean proven = false;
        try {
            if (!current.isEmpty() && tester.test(new BitSet(), "empty") == Verdict.REPRODUCES) {
                listener.onReduction("reduce-to-empty", current.cardinality(), 0, 0, new BitSet());
                return new Result(new BitSet(), true, List.of(), 0, stop);
            }
            while (true) {
                int n = 2;
                while (current.cardinality() >= 2) {
                    iterations++;
                    List<Integer> elems = ordered(current, rank);
                    int size = elems.size();
                    n = Math.min(n, size);
                    List<BitSet> parts = split(elems, n);
                    BitSet next = null;
                    String action = null;
                    int newN = n;
                    if (n > 2) { // at n == 2 subsets and complements coincide
                        List<BitSet> cands = proposals(parts, current, normalizer, refuted, false);
                        UnitTester.BatchOutcome o = tester.testFirst(cands, "subset");
                        remember(refuted, cands, o);
                        if (o.firstReproducing() >= 0) { next = cands.get(o.firstReproducing()); action = "subset"; newN = 2; }
                    }
                    if (next == null) {
                        List<BitSet> cands = proposals(parts, current, normalizer, refuted, true);
                        UnitTester.BatchOutcome o = tester.testFirst(cands, "complement");
                        remember(refuted, cands, o);
                        if (o.firstReproducing() >= 0) { next = cands.get(o.firstReproducing()); action = "complement"; newN = Math.max(n - 1, 2); }
                    }
                    if (next != null) {
                        listener.onReduction(action, size, next.cardinality(), n, next);
                        current = next;
                        n = newN;
                    } else {
                        if (n >= size) break;
                        n = Math.min(size, n * 2);
                    }
                }
                // 1-minimality proof: every single-unit removal (closure-normalised) must NOT reproduce.
                List<BitSet> removals = new ArrayList<>();
                List<Integer> removed = new ArrayList<>();
                for (int u = current.nextSetBit(0); u >= 0; u = current.nextSetBit(u + 1)) {
                    BitSet c = (BitSet) current.clone();
                    c.clear(u);
                    removals.add(normalizer.apply(c));
                    removed.add(u);
                }
                Verdict[] vs = tester.testAll(removals, "proof");
                proof = new ArrayList<>();
                int hit = -1;
                for (int i = 0; i < vs.length; i++) {
                    proof.add(new ProofEntry(removed.get(i), vs[i]));
                    if (vs[i] == Verdict.REPRODUCES && hit < 0) hit = i;
                }
                if (hit >= 0) {
                    BitSet next = removals.get(hit);
                    listener.onReduction("proof-reduction", current.cardinality(), next.cardinality(), current.cardinality(), next);
                    current = next;
                    continue;
                }
                proven = Arrays.stream(vs).allMatch(v -> v == Verdict.NOT_REPRODUCES);
                break;
            }
        } catch (BudgetExhaustedException e) {
            stop = e.kind() == BudgetExhaustedException.Kind.TIME ? StopReason.TIME_BUDGET_EXHAUSTED : StopReason.EVALUATION_BUDGET_EXHAUSTED;
            proven = false;
        } catch (SearchCancelledException e) {
            stop = StopReason.CANCELLED;
            proven = false;
        }
        return new Result(current, proven, proof, iterations, stop);
    }

    private static void remember(Set<BitSet> refuted, List<BitSet> cands, UnitTester.BatchOutcome o) {
        for (int i = 0; i < cands.size() && i < o.verdicts().length; i++)
            if (o.verdicts()[i] == Verdict.NOT_REPRODUCES) refuted.add(cands.get(i));
    }

    /** Builds candidate sets: subsets (each part) or complements (current minus part), normalised and filtered. */
    private static List<BitSet> proposals(List<BitSet> parts, BitSet current, UnaryOperator<BitSet> normalizer,
                                          Set<BitSet> refuted, boolean complement) {
        List<BitSet> out = new ArrayList<>();
        Set<BitSet> local = new HashSet<>();
        for (BitSet p : parts) {
            BitSet c = (BitSet) (complement ? diff(current, p) : p.clone());
            c = normalizer.apply(c);
            if (c.isEmpty() || c.cardinality() >= current.cardinality()) continue; // empty is tried separately
            if (refuted.contains(c) || !local.add(c)) continue;                      // duplicate suppression
            out.add(c);
        }
        return out;
    }

    private static BitSet diff(BitSet a, BitSet b) { BitSet r = (BitSet) a.clone(); r.andNot(b); return r; }

    private static List<Integer> ordered(BitSet s, int[] rank) {
        List<Integer> l = new ArrayList<>(s.cardinality());
        for (int i = s.nextSetBit(0); i >= 0; i = s.nextSetBit(i + 1)) l.add(i);
        if (rank != null) l.sort(Comparator.comparingInt(u -> rank[u]));
        return l;
    }

    /** Splits into n contiguous chunks whose sizes differ by at most one. */
    public static List<BitSet> split(List<Integer> elems, int n) {
        List<BitSet> parts = new ArrayList<>(n);
        int size = elems.size(), base = size / n, rem = size % n, pos = 0;
        for (int i = 0; i < n; i++) {
            int len = base + (i < rem ? 1 : 0);
            BitSet b = new BitSet();
            for (int j = 0; j < len; j++) b.set(elems.get(pos++));
            parts.add(b);
        }
        return parts;
    }
}
