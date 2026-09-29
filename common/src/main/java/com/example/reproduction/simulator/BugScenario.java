package com.example.reproduction.simulator;

import java.util.List;
import java.util.Set;

/**
 * A configurable bug: ordered rules, first match wins, default is HTTP 200.
 * {@code expectedMinimalSets} is documentation/test metadata only; algorithms never see it.
 */
public record BugScenario(String id, String description, List<Rule> rules, List<Set<String>> expectedMinimalSets) {

    /** {@code probability} &lt; 1 makes the rule flaky (deterministically, seeded by candidate hash + attempt). */
    public record Rule(Spec when, int status, String errorCode, String message, double probability) {
        public static Rule always(Spec when, int status, String code, String message) { return new Rule(when, status, code, message, 1.0); }
    }
}
