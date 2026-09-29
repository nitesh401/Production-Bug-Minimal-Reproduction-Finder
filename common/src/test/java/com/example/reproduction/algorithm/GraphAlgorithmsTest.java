package com.example.reproduction.algorithm;

import com.example.reproduction.algorithm.graph.*;
import com.example.reproduction.domain.Dependency;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraphAlgorithmsTest {

    private static DependencyGraph g(int n, int[]... edges) { return DependencyGraph.of(n, List.of(edges)); }

    @Test
    void tarjanFindsCyclesAndSingletons() {
        // 0<->1 cycle, 1->2, 2->3->4->2 cycle, 5 isolated
        DependencyGraph graph = g(6, new int[]{0, 1}, new int[]{1, 0}, new int[]{1, 2}, new int[]{2, 3}, new int[]{3, 4}, new int[]{4, 2});
        Tarjan.Result r = Tarjan.scc(graph);
        assertEquals(3, r.count()); // {0,1} {2,3,4} {5}
        int[] c = r.componentOf();
        assertEquals(c[0], c[1]);
        assertEquals(c[2], c[3]);
        assertEquals(c[3], c[4]);
        assertNotEquals(c[0], c[2]);
        assertNotEquals(c[5], c[0]);
    }

    @Test
    void tarjanIdsAreReverseTopological() {
        DependencyGraph graph = g(5, new int[]{0, 1}, new int[]{1, 2}, new int[]{0, 3}, new int[]{3, 2}, new int[]{2, 4});
        Tarjan.Result r = Tarjan.scc(graph);
        for (int u = 0; u < 5; u++)
            for (int v : graph.dependents(u)) assertTrue(r.componentOf()[u] > r.componentOf()[v], "edge " + u + "->" + v);
    }

    @Test
    void condensationCriticalityCountsTransitiveDependents() {
        DependencyGraph graph = g(4, new int[]{0, 1}, new int[]{1, 2}, new int[]{0, 3});
        Condensation c = new Condensation(graph, Tarjan.scc(graph));
        assertEquals(3, c.criticality(c.componentOf(0)));
        assertEquals(1, c.criticality(c.componentOf(1)));
        assertEquals(0, c.criticality(c.componentOf(2)));
        int[] topo = c.topologicalOrder();
        assertEquals(c.componentOf(0), topo[0]); // prerequisite first
    }

    @Test
    void weakComponentsIgnoreDirection() {
        DependencyGraph graph = g(6, new int[]{0, 1}, new int[]{2, 1}, new int[]{3, 4});
        WeakComponents.Result r = WeakComponents.of(graph, null);
        assertEquals(3, r.count()); // {0,1,2} {3,4} {5}
        assertEquals(r.componentOf()[0], r.componentOf()[2]);
        assertNotEquals(r.componentOf()[3], r.componentOf()[0]);
        BitSet allowed = new BitSet(); allowed.set(3); allowed.set(4); allowed.set(5);
        assertEquals(2, WeakComponents.of(graph, allowed).count());
    }

    @Test
    void transitiveDependentsAndClosure() {
        DependencyGraph graph = g(5, new int[]{0, 1}, new int[]{1, 2}, new int[]{3, 4});
        BitSet seed = new BitSet(); seed.set(0);
        BitSet dep = graph.transitiveDependents(seed);
        assertTrue(dep.get(0) && dep.get(1) && dep.get(2));
        assertFalse(dep.get(3));
        BitSet kept = new BitSet(); kept.set(1); kept.set(2);
        assertFalse(graph.isClosed(kept)); // 1 needs 0
        kept.set(0);
        assertTrue(graph.isClosed(kept));
    }

    @Test
    void analyzerBuildsGroupsFromRulesAndExplicitEdges() {
        List<String> paths = List.of("amount", "coupon", "currency", "taxType", "user.subscription.level", "user.type");
        DependencyAnalysis a = new DependencyAnalyzer(DefaultDependencyRules.paymentDomain())
                .analyze(paths, List.of(new Dependency("coupon", "taxType", "explicit")));
        assertEquals(1, a.stronglyCoupledGroups().size());
        assertEquals(List.of("amount", "currency"), a.stronglyCoupledGroups().get(0).members());
        // amount/currency -> taxType, coupon -> taxType, user.type -> level : two weak components
        assertEquals(2, a.independentGroups().size());
        assertTrue(a.criticalFields(3).contains("amount"));
        assertEquals(0, a.independentFieldCount());
    }
}
