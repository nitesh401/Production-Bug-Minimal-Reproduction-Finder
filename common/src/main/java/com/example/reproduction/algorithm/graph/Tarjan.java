package com.example.reproduction.algorithm.graph;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Tarjan's strongly-connected-components algorithm. O(V + E) time, O(V) extra space.
 * Component ids are assigned in completion order, i.e. in REVERSE topological order of the condensation:
 * for every cross-component edge u -> v, id(comp(u)) > id(comp(v)).
 * (Recursive; depth is bounded by the number of fields, which is fine for request-sized inputs.)
 */
public final class Tarjan {
    public record Result(int count, int[] componentOf) {}

    private final DependencyGraph g;
    private final int[] index, low, comp;
    private final boolean[] onStack;
    private final Deque<Integer> stack = new ArrayDeque<>();
    private int counter = 0, comps = 0;

    private Tarjan(DependencyGraph g) {
        this.g = g;
        int n = g.size();
        index = new int[n]; low = new int[n]; comp = new int[n]; onStack = new boolean[n];
        Arrays.fill(index, -1);
    }

    public static Result scc(DependencyGraph g) {
        Tarjan t = new Tarjan(g);
        for (int v = 0; v < g.size(); v++) if (t.index[v] < 0) t.visit(v);
        return new Result(t.comps, t.comp);
    }

    private void visit(int v) {
        index[v] = low[v] = counter++;
        stack.push(v);
        onStack[v] = true;
        for (int w : g.dependents(v)) {
            if (index[w] < 0) { visit(w); low[v] = Math.min(low[v], low[w]); }
            else if (onStack[w]) low[v] = Math.min(low[v], index[w]);
        }
        if (low[v] == index[v]) {
            int w;
            do { w = stack.pop(); onStack[w] = false; comp[w] = comps; } while (w != v);
            comps++;
        }
    }
}
