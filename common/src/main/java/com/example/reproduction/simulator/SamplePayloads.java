package com.example.reproduction.simulator;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SamplePayloads {
    private SamplePayloads() {}

    /** A production-like request with 27 leaf fields that triggers every built-in scenario. */
    public static Map<String, Object> payment() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("amount", 15000);
        m.put("currency", "INR");
        m.put("customerType", "PREMIUM");
        m.put("country", "IN");
        m.put("device", "MOBILE");
        m.put("language", "en");
        m.put("timezone", "IST");
        m.put("coupon", "WELCOME100");
        m.put("taxType", "GST");
        m.put("paymentMethod", "UPI");
        m.put("orderId", "ORD-88231");
        m.put("merchantId", "M-4412");
        m.put("featureFlags", map("FAST_PATH", true, "NEW_UI", false, "DARK_MODE", true, "BETA_CHECKOUT", false));
        m.put("metadata", map("source", "mobile", "campaign", "DIWALI", "referrer", "push-notification"));
        Map<String, Object> user = map("id", "U-1029", "type", "PREMIUM");
        user.put("subscription", map("level", 4));
        m.put("user", user);
        m.put("shipping", map("method", "EXPRESS", "city", "Hyderabad", "pincode", "500081"));
        m.put("session", map("id", "s-991", "retries", 2));
        return m;
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
