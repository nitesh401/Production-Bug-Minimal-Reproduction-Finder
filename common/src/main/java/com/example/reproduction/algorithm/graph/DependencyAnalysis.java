package com.example.reproduction.algorithm.graph;

import com.example.reproduction.domain.Dependency;
import com.example.reproduction.domain.InputGroup;

import java.util.ArrayList;
import java.util.List;

/** Result of dependency analysis over an ordered list of field paths (index = position in {@code paths}). */
public record DependencyAnalysis(List<String> paths, List<Dependency> dependencies, DependencyGraph graph,
                                 Condensation condensation, WeakComponents.Result weakComponents) {

    /** SCCs with more than one field: fields that must be reduced together. */
    public List<InputGroup> stronglyCoupledGroups() {
        List<InputGroup> out = new ArrayList<>();
        for (int c = 0; c < condensation.count(); c++) {
            int[] m = condensation.members(c);
            if (m.length > 1) out.add(new InputGroup(c, java.util.Arrays.stream(m).mapToObj(paths::get).sorted().toList(), "SCC"));
        }
        return out;
    }

    /** Independent reduction groups (weak components with at least one dependency edge, plus singletons). */
    public List<InputGroup> independentGroups() {
        List<List<String>> l = new ArrayList<>();
        for (int i = 0; i < weakComponents.count(); i++) l.add(new ArrayList<>());
        for (int v = 0; v < paths.size(); v++) if (weakComponents.componentOf()[v] >= 0) l.get(weakComponents.componentOf()[v]).add(paths.get(v));
        List<InputGroup> out = new ArrayList<>();
        for (int i = 0; i < l.size(); i++) { java.util.Collections.sort(l.get(i)); out.add(new InputGroup(i, l.get(i), "WEAK_COMPONENT")); }
        return out;
    }

    /** Fields on which the most other components transitively depend, most critical first. */
    public List<String> criticalFields(int limit) {
        List<Integer> comps = new ArrayList<>();
        for (int c = 0; c < condensation.count(); c++) if (condensation.criticality(c) > 0) comps.add(c);
        comps.sort((a, b) -> Integer.compare(condensation.criticality(b), condensation.criticality(a)));
        List<String> out = new ArrayList<>();
        for (int c : comps) for (int f : condensation.members(c)) { if (out.size() < limit) out.add(paths.get(f)); }
        return out;
    }

    /** Independent single fields = fields with no edges at all. */
    public int independentFieldCount() {
        int n = 0;
        for (int v = 0; v < paths.size(); v++) if (graph.dependents(v).length == 0 && graph.prerequisites(v).length == 0) n++;
        return n;
    }
}
