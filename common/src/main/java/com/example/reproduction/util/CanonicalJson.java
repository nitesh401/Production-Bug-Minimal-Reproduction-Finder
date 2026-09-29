package com.example.reproduction.util;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stable JSON serialization: sorted object keys, no whitespace, numbers normalised
 * (15000, 15000.0 and 1.5E4 all serialise as 15000). Deterministic output is the basis of candidate hashing.
 * Complexity: O(K) where K = size of the value.
 */
public final class CanonicalJson {
    private CanonicalJson() {}

    public static String write(Object v) {
        StringBuilder sb = new StringBuilder();
        append(sb, v);
        return sb.toString();
    }

    private static void append(StringBuilder sb, Object v) {
        if (v == null) sb.append("null");
        else if (v instanceof Boolean b) sb.append(b.booleanValue());
        else if (v instanceof Number n) sb.append(number(n));
        else if (v instanceof CharSequence s) quote(sb, s);
        else if (v instanceof Map<?, ?> m) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            m.forEach((k, x) -> sorted.put(String.valueOf(k), x));
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : sorted.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(sb, e.getKey());
                sb.append(':');
                append(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object x : it) {
                if (!first) sb.append(',');
                first = false;
                append(sb, x);
            }
            sb.append(']');
        } else quote(sb, v.toString());
    }

    private static String number(Number n) {
        if (n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte) return n.toString();
        try {
            BigDecimal d = new BigDecimal(n.toString()).stripTrailingZeros();
            return d.scale() < 0 ? d.setScale(0).toPlainString() : d.toPlainString();
        } catch (NumberFormatException e) {
            return "\"" + n + "\"";
        }
    }

    private static void quote(StringBuilder sb, CharSequence s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
