package com.example.reproduction.simulator;

import com.example.reproduction.simulator.BugScenario.Rule;

import java.util.*;

import static com.example.reproduction.simulator.Spec.*;

/** Built-in, increasingly difficult bug scenarios for the payment endpoint. */
public final class Scenarios {
    private Scenarios() {}

    private static final Spec HIGH_INR = and(new Gt("amount", 10000), new Eq("currency", "INR"));

    public static final BugScenario SIMPLE_AND = new BugScenario("SIMPLE_AND",
            "1: amount>10000 AND currency=INR AND FAST_PATH AND customerType=PREMIUM",
            List.of(Rule.always(and(HIGH_INR, new Eq("featureFlags.FAST_PATH", true), new Eq("customerType", "PREMIUM")),
                    500, "PAYMENT_ROUTE_FAILURE", "no route for premium fast path")),
            List.of(Set.of("amount", "currency", "featureFlags.FAST_PATH", "customerType")));

    public static final BugScenario OR_BRANCH = new BugScenario("OR_BRANCH",
            "2: amount>10000 AND currency=INR AND (FAST_PATH OR coupon starts with WELCOME) => 2 minimal sets",
            List.of(Rule.always(and(HIGH_INR, or(new Eq("featureFlags.FAST_PATH", true), new StartsWith("coupon", "WELCOME"))),
                    500, "PAYMENT_ROUTE_FAILURE", "promo or fast path routing")),
            List.of(Set.of("amount", "currency", "featureFlags.FAST_PATH"), Set.of("amount", "currency", "coupon")));

    public static final BugScenario NESTED_JSON = new BugScenario("NESTED_JSON",
            "3: nested: metadata.campaign=DIWALI AND user.subscription.level>=3 AND user.type=PREMIUM (level needs type)",
            List.of(
                    Rule.always(and(new Present("user.subscription.level"), not(new Present("user.type"))), 400, "VALIDATION_ERROR", "user.type required"),
                    Rule.always(and(new Eq("metadata.campaign", "DIWALI"), new Gte("user.subscription.level", 3), new Eq("user.type", "PREMIUM")),
                            500, "PAYMENT_ROUTE_FAILURE", "campaign tier routing")),
            List.of(Set.of("metadata.campaign", "user.subscription.level", "user.type")));

    /**
     * 4: the dependency trap. Removing a partner field crashes the service with a DIFFERENT 500, so plain ddmin
     * (signature: any HTTP 500) "minimises" to a spurious 1-field input. Dependency-aware reduction never builds
     * such malformed candidates and finds the real bug.
     */
    public static final BugScenario DEPENDENCY_TRAP = new BugScenario("DEPENDENCY_TRAP",
            "4: real bug needs amount+currency+taxType+FAST_PATH; malformed inputs also return 500 (spurious)",
            List.of(
                    Rule.always(and(new Present("amount"), not(new Present("currency"))), 500, "CURRENCY_MISSING", "NPE: currency"),
                    Rule.always(and(new Present("currency"), not(new Present("amount"))), 500, "AMOUNT_MISSING", "NPE: amount"),
                    Rule.always(and(new Present("taxType"), not(new Present("amount"))), 500, "TAX_BASE_MISSING", "NPE: tax base"),
                    Rule.always(and(HIGH_INR, new Eq("taxType", "GST"), new Eq("featureFlags.FAST_PATH", true)),
                            500, "PAYMENT_ROUTE_FAILURE", "gst fast path")),
            List.of(Set.of("amount", "currency", "taxType", "featureFlags.FAST_PATH")));

    /** 5: flaky: fires with probability 0.85 per attempt. Use evaluationAttempts >= 5, minimumReproductionRate ~0.6. */
    public static final BugScenario FLAKY = new BugScenario("FLAKY",
            "5: currency=INR AND amount>10000 AND device=MOBILE, reproduces ~85% of attempts",
            List.of(new Rule(and(HIGH_INR, new Eq("device", "MOBILE")), 500, "PAYMENT_ROUTE_FAILURE", "intermittent", 0.85)),
            List.of(Set.of("amount", "currency", "device")));

    public static final BugScenario MULTI_MINIMAL = new BugScenario("MULTI_MINIMAL",
            "6: (amount>10000 + INR + PREMIUM) OR (country=IN + coupon=WELCOME100)",
            List.of(Rule.always(or(and(HIGH_INR, new Eq("customerType", "PREMIUM")), and(new Eq("country", "IN"), new Eq("coupon", "WELCOME100"))),
                    500, "PAYMENT_ROUTE_FAILURE", "two independent triggers")),
            List.of(Set.of("amount", "currency", "customerType"), Set.of("country", "coupon")));

    public static Map<String, BugScenario> builtIn() {
        Map<String, BugScenario> m = new LinkedHashMap<>();
        for (BugScenario s : List.of(SIMPLE_AND, OR_BRANCH, NESTED_JSON, DEPENDENCY_TRAP, FLAKY, MULTI_MINIMAL)) m.put(s.id(), s);
        return m;
    }
}
