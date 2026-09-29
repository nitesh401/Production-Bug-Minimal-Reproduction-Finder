package com.example.reproduction.simulator.application;

import com.example.reproduction.simulator.BugScenario;
import com.example.reproduction.simulator.Scenarios;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Built-in scenarios plus scenarios registered at runtime (in memory: the simulator is a test double). */
@Component
public class ScenarioRegistry {
    private final Map<String, BugScenario> scenarios = new ConcurrentHashMap<>(Scenarios.builtIn());

    public Optional<BugScenario> find(String id) { return Optional.ofNullable(scenarios.get(id)); }
    public Collection<BugScenario> all() { return scenarios.values(); }
    public boolean builtIn(String id) { return Scenarios.builtIn().containsKey(id); }
    public void register(BugScenario s) { scenarios.put(s.id(), s); }
}
