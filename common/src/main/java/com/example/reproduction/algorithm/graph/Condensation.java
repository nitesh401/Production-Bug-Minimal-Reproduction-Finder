package com.example.reproduction.algorithm.graph;

import java.util.*;

/**
 * The DAG of strongly connected components. Also computes, for every component, its criticality =
 * number of OTHER components that transitively depend on it (bitset DP over the DAG: O(C * (C/64 + E))).
 */
public final class Condensation {
    private final int count;
    private final int[] componentOf;
    private final int[][] members;
    private final int[][] prerequisites; // direct prerequisite components
    private final int[][] dependents;    // direct dependent components
    private final int[] criticality;

    public Condensation(DependencyGraph g, Tarjan.Result scc) {
        this.count = scc.count();
        this.componentOf = scc.componentOf();
        int[] sizes = new int[count];
        for (int v = 0; v < g.size(); v++) sizes[componentOf[v]]++;
        members = new int[count][];
        for (int c = 0; c < count; c++) members[c] = new int[sizes[c]];
        int[] fill = new int[count];
        for (int v = 0; v < g.size(); v++) members[componentOf[v]][fill[componentOf[v]]++] = v;

        List<Set<Integer>> pre = new ArrayList<>(), dep = new ArrayList<>();
        for (int c = 0; c < count; c++) { pre.add(new TreeSet<>()); dep.add(new TreeSet<>()); }
        for (int u = 0; u < g.size(); u++)
            for (int v : g.dependents(u)) {
                int cu = componentOf[u], cv = componentOf[v];
                if (cu != cv) { dep.get(cu).add(cv); pre.get(cv).add(cu); }
            }
        prerequisites = toArrays(pre);
        dependents = toArrays(dep);

        // ids ascend from sinks (no dependents) to sources; dependents of c always have smaller ids.
        BitSet[] reach = new BitSet[count];
        criticality = new int[count];
        for (int c = 0; c < count; c++) {
            BitSet r = new BitSet(count);
            for (int d : dependents[c]) { r.set(d); r.or(reach[d]); }
            reach[c] = r;
            criticality[c] = r.cardinality();
        }
    }

    private static int[][] toArrays(List<Set<Integer>> l) {
        int[][] a = new int[l.size()][];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i).stream().mapToInt(Integer::intValue).toArray();
        return a;
    }

    public int count() { return count; }
    public int componentOf(int field) { return componentOf[field]; }
    public int[] members(int comp) { return members[comp]; }
    public int[] prerequisites(int comp) { return prerequisites[comp]; }
    public int[] dependents(int comp) { return dependents[comp]; }
    public int criticality(int comp) { return criticality[comp]; }

    /** Component ids in topological order: prerequisites before dependents (= descending Tarjan ids). */
    public int[] topologicalOrder() {
        int[] o = new int[count];
        for (int i = 0; i < count; i++) o[i] = count - 1 - i;
        return o;
    }
}
