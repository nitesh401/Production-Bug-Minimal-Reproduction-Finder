package com.example.reproduction.evaluation;

import com.example.reproduction.domain.EvaluationResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Small LRU cache (used as L1 in front of Redis, and standalone in tests/demo). */
public final class InMemoryEvaluationCache implements EvaluationCache {
    private final Map<String, EvaluationResult> map;

    public InMemoryEvaluationCache(int maxEntries) {
        this.map = new LinkedHashMap<>(256, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, EvaluationResult> e) { return size() > maxEntries; }
        };
    }

    @Override public synchronized Optional<EvaluationResult> get(String key) { return Optional.ofNullable(map.get(key)); }
    @Override public synchronized void put(String key, EvaluationResult r) { map.put(key, r); }
    public synchronized int size() { return map.size(); }
}
