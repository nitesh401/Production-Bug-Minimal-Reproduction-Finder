package com.example.reproduction.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Flattens nested JSON-like maps into dotted leaf paths ("featureFlags.FAST_PATH") and back.
 * Lists are treated as atomic leaves. Keys must not contain '.'.
 */
public final class InputFlattener {
    private InputFlattener() {}

    public static Map<String, Object> flatten(Map<String, ?> nested) {
        Map<String, Object> out = new LinkedHashMap<>();
        walk("", nested, out);
        return out;
    }

    private static void walk(String prefix, Map<?, ?> m, Map<String, Object> out) {
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String k = String.valueOf(e.getKey());
            String path = prefix.isEmpty() ? k : prefix + "." + k;
            if (e.getValue() instanceof Map<?, ?> sub) walk(path, sub, out);
            else out.put(path, e.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> unflatten(Map<String, Object> flat) {
        Map<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : flat.entrySet()) {
            String[] parts = e.getKey().split("\\.");
            Map<String, Object> cur = root;
            for (int i = 0; i < parts.length - 1; i++)
                cur = (Map<String, Object>) cur.computeIfAbsent(parts[i], k -> new LinkedHashMap<String, Object>());
            cur.put(parts[parts.length - 1], e.getValue());
        }
        return root;
    }

    /** Null-safe dotted lookup in a nested map. */
    public static Object get(Map<String, Object> nested, String path) {
        Object cur = nested;
        for (String p : path.split("\\.")) {
            if (!(cur instanceof Map<?, ?> m) || !m.containsKey(p)) return null;
            cur = m.get(p);
        }
        return cur;
    }

    public static boolean has(Map<String, Object> nested, String path) {
        Object cur = nested;
        for (String p : path.split("\\.")) {
            if (!(cur instanceof Map<?, ?> m) || !m.containsKey(p)) return false;
            cur = m.get(p);
        }
        return true;
    }
}
