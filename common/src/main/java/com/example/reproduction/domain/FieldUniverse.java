package com.example.reproduction.domain;

import com.example.reproduction.util.InputFlattener;

import java.util.*;

/** The fixed, lexicographically ordered set of leaf fields of the original failing input. */
public final class FieldUniverse {
    private final List<String> paths;
    private final Object[] values;
    private final Map<String, Integer> index = new HashMap<>();
    private final int[] sizes;

    public FieldUniverse(Map<String, Object> flat) {
        TreeMap<String, Object> sorted = new TreeMap<>(flat);
        this.paths = List.copyOf(sorted.keySet());
        this.values = sorted.values().toArray();
        this.sizes = new int[paths.size()];
        for (int i = 0; i < paths.size(); i++) {
            index.put(paths.get(i), i);
            sizes[i] = new InputField(paths.get(i), values[i]).payloadSize();
        }
    }

    public static FieldUniverse ofNested(Map<String, Object> nested) { return new FieldUniverse(InputFlattener.flatten(nested)); }

    public int size() { return paths.size(); }
    public String path(int i) { return paths.get(i); }
    public List<String> paths() { return paths; }
    public Object value(int i) { return values[i]; }
    public int payloadSize(int i) { return sizes[i]; }
    public int indexOf(String path) {
        Integer i = index.get(path);
        if (i == null) throw new IllegalArgumentException("Unknown field: " + path);
        return i;
    }

    public BitSet all() { BitSet b = new BitSet(size()); b.set(0, size()); return b; }

    public BitSet of(Collection<String> ps) { BitSet b = new BitSet(size()); ps.forEach(p -> b.set(indexOf(p))); return b; }

    public List<String> pathsOf(BitSet fields) {
        List<String> out = new ArrayList<>(fields.cardinality());
        for (int i = fields.nextSetBit(0); i >= 0; i = fields.nextSetBit(i + 1)) out.add(paths.get(i));
        return out;
    }

    public Candidate candidateOf(BitSet fields) {
        Map<String, Object> m = new TreeMap<>();
        for (int i = fields.nextSetBit(0); i >= 0; i = fields.nextSetBit(i + 1)) m.put(paths.get(i), values[i]);
        return Candidate.of(m);
    }
}
