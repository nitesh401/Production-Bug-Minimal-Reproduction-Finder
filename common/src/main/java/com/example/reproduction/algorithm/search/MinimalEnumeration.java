package com.example.reproduction.algorithm.search;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Enumerates ALL 1-minimal reproductions by branching on bans (a hitting-set style search):
 * after finding a minimal set M under ban B, every different minimal set must exclude at least one element of M,
 * so each branch bans one more element of M. A set found under any ban is minimal globally, because removing any of
 * its elements yields a subset of the allowed space that was proven not to reproduce.
 * Complete when the predicate is monotone and the budget suffices; bounded by {@code maxSolutions}.
 */
public final class MinimalEnumeration {
    private MinimalEnumeration() {}

    public static List<BitSet> nextBans(BitSet currentBan, BitSet minimal) {
        List<BitSet> out = new ArrayList<>();
        for (int f = minimal.nextSetBit(0); f >= 0; f = minimal.nextSetBit(f + 1)) {
            BitSet b = (BitSet) currentBan.clone();
            b.set(f);
            out.add(b);
        }
        return out;
    }
}
