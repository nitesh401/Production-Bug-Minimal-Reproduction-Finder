package com.example.reproduction.algorithm.graph;

/**
 * Weakly connected components (edge direction ignored) via union-find with path compression and
 * union by size: O((V + E) * alpha(V)). These are the independent reduction groups: no dependency edge
 * crosses two different components, so they can be kept/removed wholesale.
 */
public final class WeakComponents {
    public record Result(int count, int[] componentOf) {}

    private WeakComponents() {}

    public static Result of(DependencyGraph g, java.util.BitSet allowed) {
        int n = g.size();
        int[] parent = new int[n], size = new int[n];
        for (int i = 0; i < n; i++) { parent[i] = i; size[i] = 1; }
        for (int u = 0; u < n; u++) {
            if (allowed != null && !allowed.get(u)) continue;
            for (int v : g.dependents(u)) {
                if (allowed != null && !allowed.get(v)) continue;
                int a = find(parent, u), b = find(parent, v);
                if (a == b) continue;
                if (size[a] < size[b]) { int t = a; a = b; b = t; }
                parent[b] = a;
                size[a] += size[b];
            }
        }
        int[] id = new int[n];
        java.util.Arrays.fill(id, -1);
        int[] compOf = new int[n];
        int count = 0;
        for (int v = 0; v < n; v++) {
            if (allowed != null && !allowed.get(v)) { compOf[v] = -1; continue; }
            int r = find(parent, v);
            if (id[r] < 0) id[r] = count++;
            compOf[v] = id[r];
        }
        return new Result(count, compOf);
    }

    private static int find(int[] p, int x) {
        while (p[x] != x) { p[x] = p[p[x]]; x = p[x]; }
        return x;
    }
}
