package com.example.reproduction.algorithm.search;

import com.example.reproduction.algorithm.graph.Condensation;
import com.example.reproduction.algorithm.graph.DependencyAnalysis;
import com.example.reproduction.algorithm.graph.WeakComponents;
import com.example.reproduction.domain.FieldUniverse;

import java.util.*;

/**
 * The abstraction ddmin reduces over. A "unit" is a set of fields that is kept or removed atomically:
 * a single field (plain ddmin), an SCC (dependency-aware) or a weak component (group-level).
 * Units of an SCC space are numbered in topological order (prerequisites first), so {@link #normalize}
 * is a single O(U + E) pass that drops every unit whose prerequisite unit is missing.
 */
public final class UnitSpace {
    private final BitSet[] unitFields;
    private final int[][] prereqs;
    private final int[] criticality;

    private UnitSpace(BitSet[] unitFields, int[][] prereqs, int[] criticality) {
        this.unitFields = unitFields; this.prereqs = prereqs; this.criticality = criticality;
    }

    public int size() { return unitFields.length; }
    public BitSet allUnits() { BitSet b = new BitSet(size()); b.set(0, size()); return b; }
    public BitSet fieldsOf(int unit) { return unitFields[unit]; }

    public BitSet expand(BitSet units) {
        BitSet f = new BitSet();
        for (int u = units.nextSetBit(0); u >= 0; u = units.nextSetBit(u + 1)) f.or(unitFields[u]);
        return f;
    }

    /** Drops every kept unit whose (transitive) prerequisites are not all kept. */
    public BitSet normalize(BitSet units) {
        BitSet out = new BitSet(size());
        for (int u = units.nextSetBit(0); u >= 0; u = units.nextSetBit(u + 1)) {
            boolean ok = true;
            for (int p : prereqs[u]) if (!out.get(p)) { ok = false; break; }
            if (ok) out.set(u);
        }
        return out;
    }

    /** rank[u] = position of unit u in the "likely removable first" order (low keep-hint, low criticality, big payload). */
    public int[] priorityRank(FieldUniverse universe, Map<String, Double> keepHints) {
        Integer[] ids = new Integer[size()];
        double[] hint = new double[size()];
        long[] payload = new long[size()];
        for (int u = 0; u < size(); u++) {
            ids[u] = u;
            for (int f = unitFields[u].nextSetBit(0); f >= 0; f = unitFields[u].nextSetBit(f + 1)) {
                hint[u] = Math.max(hint[u], keepHints.getOrDefault(universe.path(f), 0.0));
                payload[u] += universe.payloadSize(f);
            }
        }
        Arrays.sort(ids, Comparator.<Integer>comparingDouble(u -> hint[u])
                .thenComparingInt(u -> criticality[u]).thenComparing(u -> -payload[u]).thenComparingInt(u -> u));
        int[] rank = new int[size()];
        for (int i = 0; i < ids.length; i++) rank[ids[i]] = i;
        return rank;
    }

    public int[] randomRank(long seed) {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < size(); i++) l.add(i);
        Collections.shuffle(l, new Random(seed));
        int[] rank = new int[size()];
        for (int i = 0; i < l.size(); i++) rank[l.get(i)] = i;
        return rank;
    }

    /** One unit per field, no dependency semantics (classic ddmin). */
    public static UnitSpace singletons(BitSet fields, DependencyAnalysis analysis) {
        int n = fields.cardinality();
        BitSet[] uf = new BitSet[n];
        int[][] pre = new int[n][0];
        int[] crit = new int[n];
        int i = 0;
        for (int f = fields.nextSetBit(0); f >= 0; f = fields.nextSetBit(f + 1), i++) {
            uf[i] = new BitSet(); uf[i].set(f);
            if (analysis != null) crit[i] = analysis.condensation().criticality(analysis.condensation().componentOf(f));
        }
        return new UnitSpace(uf, pre, crit);
    }

    /**
     * SCC units restricted to {@code allowed}, minus {@code banned}. A component is a unit only if all its members
     * are allowed, none banned and all its prerequisite components are units (so dependents of banned units vanish).
     */
    public static UnitSpace scc(DependencyAnalysis a, BitSet allowed, BitSet banned) {
        Condensation c = a.condensation();
        int[] unitOfComp = new int[c.count()];
        Arrays.fill(unitOfComp, -1);
        List<BitSet> fields = new ArrayList<>();
        List<int[]> pre = new ArrayList<>();
        List<Integer> crit = new ArrayList<>();
        for (int comp : c.topologicalOrder()) {
            boolean ok = true;
            BitSet f = new BitSet();
            for (int m : c.members(comp)) {
                if (!allowed.get(m) || (banned != null && banned.get(m))) { ok = false; break; }
                f.set(m);
            }
            if (!ok) continue;
            List<Integer> ps = new ArrayList<>();
            for (int p : c.prerequisites(comp)) {
                if (unitOfComp[p] < 0) { ok = false; break; }
                ps.add(unitOfComp[p]);
            }
            if (!ok) continue;
            unitOfComp[comp] = fields.size();
            fields.add(f);
            pre.add(ps.stream().mapToInt(Integer::intValue).toArray());
            crit.add(c.criticality(comp));
        }
        return new UnitSpace(fields.toArray(new BitSet[0]), pre.toArray(new int[0][]), crit.stream().mapToInt(Integer::intValue).toArray());
    }

    /** Independent groups: weak components of the dependency graph induced on {@code allowed}. */
    public static UnitSpace weakComponents(DependencyAnalysis a, BitSet allowed) {
        WeakComponents.Result r = WeakComponents.of(a.graph(), allowed);
        BitSet[] uf = new BitSet[r.count()];
        for (int i = 0; i < uf.length; i++) uf[i] = new BitSet();
        for (int f = allowed.nextSetBit(0); f >= 0; f = allowed.nextSetBit(f + 1)) uf[r.componentOf()[f]].set(f);
        return new UnitSpace(uf, new int[uf.length][0], new int[uf.length]);
    }
}
