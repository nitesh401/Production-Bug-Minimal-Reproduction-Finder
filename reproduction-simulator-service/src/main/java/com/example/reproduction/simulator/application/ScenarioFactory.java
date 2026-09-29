package com.example.reproduction.simulator.application;

import com.example.reproduction.simulator.BugScenario;
import com.example.reproduction.simulator.Spec;
import com.example.reproduction.simulator.api.CustomScenarioRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Factory: wire DTO -> domain Specification objects. */
public final class ScenarioFactory {
    private ScenarioFactory() {}

    public static BugScenario create(CustomScenarioRequest r) {
        List<BugScenario.Rule> rules = new ArrayList<>();
        for (CustomScenarioRequest.RuleDto d : r.rules()) {
            List<Spec> groups = new ArrayList<>();
            for (List<CustomScenarioRequest.ClauseDto> g : d.anyOf()) groups.add(new Spec.And(g.stream().map(ScenarioFactory::clause).toList()));
            Spec when = groups.size() == 1 ? groups.get(0) : new Spec.Or(groups);
            rules.add(new BugScenario.Rule(when, d.status(), d.errorCode(), d.message() == null ? "" : d.message(), d.probability() == null ? 1.0 : d.probability()));
        }
        return new BugScenario(r.id(), r.description() == null ? "custom scenario" : r.description(), rules, List.<Set<String>>of());
    }

    private static Spec clause(CustomScenarioRequest.ClauseDto c) {
        return switch (c.op().toUpperCase()) {
            case "EQ" -> new Spec.Eq(c.path(), c.value());
            case "GT" -> new Spec.Gt(c.path(), number(c));
            case "GTE" -> new Spec.Gte(c.path(), number(c));
            case "STARTS_WITH" -> new Spec.StartsWith(c.path(), String.valueOf(c.value()));
            case "PRESENT" -> new Spec.Present(c.path());
            case "ABSENT" -> new Spec.Not(new Spec.Present(c.path()));
            default -> throw new IllegalArgumentException("Unknown operator: " + c.op() + " (EQ, GT, GTE, STARTS_WITH, PRESENT, ABSENT)");
        };
    }

    private static double number(CustomScenarioRequest.ClauseDto c) {
        if (c.value() instanceof Number n) return n.doubleValue();
        throw new IllegalArgumentException("Operator " + c.op() + " needs a numeric value");
    }
}
