package com.example.reproduction.algorithm.search;

import com.example.reproduction.domain.EvaluationStatus;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Dominance pruning under the MONOTONICITY assumption (adding fields never makes a reproducing input stop
 * reproducing, removing fields never makes a non-reproducing input start):
 * <ul>
 *   <li>superset of a known REPRODUCING set  =&gt; reproduces (no evaluation needed);</li>
 *   <li>subset of a known NON-reproducing set =&gt; does not reproduce.</li>
 * </ul>
 * Only conclusive results are recorded. We keep antichains (minimal reproducing / maximal non-reproducing sets),
 * so a lookup costs O(K * |F|) with K = antichain size.
 */
public final class DominanceIndex {
    private final List<BitSet> reproducing = new ArrayList<>();
    private final List<BitSet> nonReproducing = new ArrayList<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /** @return inferred status, or null if nothing can be concluded. */
    public EvaluationStatus infer(BitSet fields) {
        lock.readLock().lock();
        try {
            for (BitSet r : reproducing) if (subset(r, fields)) return EvaluationStatus.REPRODUCES_BUG;
            for (BitSet n : nonReproducing) if (subset(fields, n)) return EvaluationStatus.DOES_NOT_REPRODUCE;
            return null;
        } finally { lock.readLock().unlock(); }
    }

    public void record(BitSet fields, EvaluationStatus status) {
        if (!status.conclusive()) return;
        lock.writeLock().lock();
        try {
            BitSet f = (BitSet) fields.clone();
            if (status == EvaluationStatus.REPRODUCES_BUG) {
                for (BitSet r : reproducing) if (subset(r, f)) return;          // already implied
                reproducing.removeIf(r -> subset(f, r));                         // supersets become redundant
                reproducing.add(f);
            } else {
                for (BitSet n : nonReproducing) if (subset(f, n)) return;
                nonReproducing.removeIf(n -> subset(n, f));
                nonReproducing.add(f);
            }
        } finally { lock.writeLock().unlock(); }
    }

    public int size() { return reproducing.size() + nonReproducing.size(); }

    static boolean subset(BitSet a, BitSet b) {
        for (int i = a.nextSetBit(0); i >= 0; i = a.nextSetBit(i + 1)) if (!b.get(i)) return false;
        return true;
    }
}
