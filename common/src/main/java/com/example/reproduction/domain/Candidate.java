package com.example.reproduction.domain;

import com.example.reproduction.util.CanonicalJson;
import com.example.reproduction.util.Hashing;
import com.example.reproduction.util.InputFlattener;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A retained subset of the flattened input. Canonical form = sorted "path=canonicalJson(value)" lines;
 * candidateHash = SHA-256(canonical form). Hash/canonical form are computed lazily and memoised.
 */
public final class Candidate {
    private final TreeMap<String, Object> fields;
    private volatile String canonical;
    private volatile String hash;

    private Candidate(TreeMap<String, Object> fields) { this.fields = fields; }

    public static Candidate of(Map<String, Object> flat) { return new Candidate(new TreeMap<>(flat)); }

    public int size() { return fields.size(); }
    public Set<String> paths() { return Collections.unmodifiableSet(fields.keySet()); }
    public Map<String, Object> flat() { return Collections.unmodifiableMap(fields); }
    public Map<String, Object> nested() { return InputFlattener.unflatten(fields); }

    public String canonical() {
        String c = canonical;
        if (c == null) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Object> e : fields.entrySet())
                sb.append(e.getKey()).append('=').append(CanonicalJson.write(e.getValue())).append('\n');
            canonical = c = sb.toString();
        }
        return c;
    }

    public String hash() {
        String h = hash;
        if (h == null) hash = h = Hashing.sha256Hex(canonical());
        return h;
    }

    public int payloadSize() { return canonical().length(); }

    @Override public boolean equals(Object o) { return o instanceof Candidate c && c.hash().equals(hash()); }
    @Override public int hashCode() { return hash().hashCode(); }
    @Override public String toString() { return "Candidate" + fields.keySet(); }
}
