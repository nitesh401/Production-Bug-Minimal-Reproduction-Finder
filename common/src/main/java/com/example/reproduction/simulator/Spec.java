package com.example.reproduction.simulator;

import com.example.reproduction.util.InputFlattener;

import java.util.List;
import java.util.Map;

/** Specification pattern: composable predicates over the (nested) request. */
public sealed interface Spec {
    boolean test(Map<String, Object> in);

    record Present(String path) implements Spec { public boolean test(Map<String, Object> in) { return InputFlattener.has(in, path); } }
    record Eq(String path, Object value) implements Spec {
        public boolean test(Map<String, Object> in) {
            Object v = InputFlattener.get(in, path);
            if (v == null) return false;
            if (v instanceof Number a && value instanceof Number b) return a.doubleValue() == b.doubleValue();
            return v.equals(value);
        }
    }
    record Gt(String path, double min) implements Spec {
        public boolean test(Map<String, Object> in) { return InputFlattener.get(in, path) instanceof Number n && n.doubleValue() > min; }
    }
    record Gte(String path, double min) implements Spec {
        public boolean test(Map<String, Object> in) { return InputFlattener.get(in, path) instanceof Number n && n.doubleValue() >= min; }
    }
    record StartsWith(String path, String prefix) implements Spec {
        public boolean test(Map<String, Object> in) { return InputFlattener.get(in, path) instanceof String s && s.startsWith(prefix); }
    }
    record And(List<Spec> parts) implements Spec { public boolean test(Map<String, Object> in) { return parts.stream().allMatch(p -> p.test(in)); } }
    record Or(List<Spec> parts) implements Spec { public boolean test(Map<String, Object> in) { return parts.stream().anyMatch(p -> p.test(in)); } }
    record Not(Spec inner) implements Spec { public boolean test(Map<String, Object> in) { return !inner.test(in); } }

    static Spec and(Spec... s) { return new And(List.of(s)); }
    static Spec or(Spec... s) { return new Or(List.of(s)); }
    static Spec not(Spec s) { return new Not(s); }
}
