package com.example.reproduction.algorithm.graph;

import java.util.List;

public final class DefaultDependencyRules {
    private DefaultDependencyRules() {}

    /** Domain knowledge for the payment example; real deployments load these from configuration. */
    public static List<DependencyRule> paymentDomain() {
        return List.of(
                new RelatedGroupRule(List.of("amount", "currency"), "monetary value needs its currency"),
                new PrerequisiteRule("amount", "taxType", "tax is computed on an amount"),
                new PrerequisiteRule("user.type", "user.subscription.level", "subscription level is defined per user type"),
                new RelatedGroupRule(List.of("startDate", "endDate"), "a date range is meaningless with one end"),
                new RelatedGroupRule(List.of("shipping.city", "shipping.pincode"), "address parts belong together"));
    }
}
