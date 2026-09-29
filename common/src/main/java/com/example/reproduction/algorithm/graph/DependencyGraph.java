package com.example.reproduction.algorithm.graph;

import java.util.*;

/**
 * Directed graph over fields 0..n-1. Edge u -> v means "v depends on u" (keeping v requires keeping u).
 * Immutable, CSR-like adjacency arrays. Memory O(V + E).
 */
public final class DependencyGraph {
    private final int n;
    private final int[][] out;
    private final int[][] in;
    private final int edgeCount;

    private DependencyGraph(int n, int[][] out, int[][] in, int edgeCount) {
        this.n = n; this.out = out; this.in = in; this.edgeCount = edgeCount;
    }

    public static DependencyGraph empty(int n) { return of(n, List.of()); }

    /** Builds from edge pairs {from,to}; duplicates and self-loops are dropped. O(V + E log E). */
    public static DependencyGraph of(int n, Collection<int[]> edges) {
        TreeSet<Long> unique = new TreeSet<>();
        for (int[] e : edges) {
            if (e[0] == e[1]) continue;
            if (e[0] < 0 || e[1] < 0 || e[0] >= n || e[1] >= n) throw new IllegalArgumentException("edge out of range");
            unique.add(((long) e[0] << 32) | e[1]);
        }
        int[] outDeg = new int[n], inDeg = new int[n];
        for (long k : unique) { outDeg[(int) (k >>> 32)]++; inDeg[(int) k]++; }
        int[][] out = new int[n][], in = new int[n][];
        for (int i = 0; i < n; i++) { out[i] = new int[outDeg[i]]; in[i] = new int[inDeg[i]]; }
        int[] oi = new int[n], ii = new int[n];
        for (long k : unique) {
            int u = (int) (k >>> 32), v = (int) k;
            out[u][oi[u]++] = v;
            in[v][ii[v]++] = u;
        }
        return new DependencyGraph(n, out, in, unique.size());
    }

    public int size() { return n; }
    public int edgeCount() { return edgeCount; }
    /** Fields that depend on u. */
    public int[] dependents(int u) { return out[u]; }
    /** Fields u depends on (its prerequisites). */
    public int[] prerequisites(int u) { return in[u]; }

    /** BFS over dependents: everything that transitively depends on any seed (seeds included). O(V + E). */
    public BitSet transitiveDependents(BitSet seeds) {
        BitSet seen = (BitSet) seeds.clone();
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int i = seeds.nextSetBit(0); i >= 0; i = seeds.nextSetBit(i + 1)) q.add(i);
        while (!q.isEmpty()) {
            int u = q.poll();
            for (int v : out[u]) if (!seen.get(v)) { seen.set(v); q.add(v); }
        }
        return seen;
    }

    /** True iff every kept field has all its prerequisites kept. */
    public boolean isClosed(BitSet kept) {
        for (int v = kept.nextSetBit(0); v >= 0; v = kept.nextSetBit(v + 1))
            for (int p : in[v]) if (!kept.get(p)) return false;
        return true;
    }
}
