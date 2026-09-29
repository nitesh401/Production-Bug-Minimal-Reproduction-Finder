package com.example.reproduction.algorithm.graph;

import com.example.reproduction.domain.Dependency;

import java.util.*;

/** Builds the dependency graph from explicit edges and inference rules, then runs SCC and component analysis. */
public final class DependencyAnalyzer {
    private final List<DependencyRule> rules;

    public DependencyAnalyzer(List<DependencyRule> rules) { this.rules = List.copyOf(rules); }

    public DependencyAnalysis analyze(List<String> paths, List<Dependency> explicit) {
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < paths.size(); i++) idx.put(paths.get(i), i);
        List<Dependency> all = new ArrayList<>(explicit == null ? List.of() : explicit);
        for (DependencyRule r : rules) all.addAll(r.infer(paths));
        List<int[]> edges = new ArrayList<>();
        List<Dependency> kept = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Dependency d : all) {
            Integer a = idx.get(d.from()), b = idx.get(d.to());
            if (a == null || b == null || a.equals(b)) continue; // unknown fields are ignored, not fatal
            if (seen.add(a + ">" + b)) { edges.add(new int[]{a, b}); kept.add(d); }
        }
        DependencyGraph g = DependencyGraph.of(paths.size(), edges);
        Condensation c = new Condensation(g, Tarjan.scc(g));
        return new DependencyAnalysis(List.copyOf(paths), kept, g, c, WeakComponents.of(g, null));
    }
}
