package com.example.reproduction.simulator.api;

import com.example.reproduction.domain.Candidate;
import com.example.reproduction.domain.ResponseObservation;
import com.example.reproduction.simulator.BugScenario;
import com.example.reproduction.simulator.ScenarioEngine;
import com.example.reproduction.simulator.application.ScenarioFactory;
import com.example.reproduction.simulator.application.ScenarioRegistry;
import com.example.reproduction.util.InputFlattener;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class SimulatorController {
    private final ScenarioRegistry registry;
    private final String defaultScenario;

    public SimulatorController(ScenarioRegistry registry, @Value("${simulator.default-scenario:SIMPLE_AND}") String defaultScenario) {
        this.registry = registry;
        this.defaultScenario = defaultScenario;
    }

    public record ScenarioView(String id, String description, boolean builtIn, List<?> expectedMinimalSets) {}
    public record TestRequest(String scenarioId, Map<String, Object> input, Integer attempt) {}

    /**
     * The simulated production endpoint. Headers: X-Bug-Scenario (scenario id), X-Attempt (0-based, seeds flakiness),
     * X-Candidate-Hash (seeds flakiness; derived from the body if absent), X-Chaos (TIMEOUT | UNAVAILABLE) to exercise
     * the caller's failure handling.
     */
    @PostMapping(value = "/simulate/payment", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> payment(@RequestBody Map<String, Object> body,
                                                        @RequestHeader(value = "X-Bug-Scenario", required = false) String scenarioId,
                                                        @RequestHeader(value = "X-Attempt", defaultValue = "0") int attempt,
                                                        @RequestHeader(value = "X-Candidate-Hash", required = false) String hash,
                                                        @RequestHeader(value = "X-Chaos", required = false) String chaos) throws InterruptedException {
        if ("TIMEOUT".equalsIgnoreCase(chaos)) Thread.sleep(30_000);
        if ("UNAVAILABLE".equalsIgnoreCase(chaos)) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "chaos");
        BugScenario s = registry.find(scenarioId == null ? defaultScenario : scenarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown scenario " + scenarioId));
        String h = hash != null ? hash : Candidate.of(InputFlattener.flatten(body)).hash();
        ResponseObservation o = ScenarioEngine.run(s, body, h, attempt);
        Map<String, Object> out = new LinkedHashMap<>();
        if (o.errorCode() != null) out.put("errorCode", o.errorCode());
        out.put("message", o.status() == 200 ? "OK" : o.body());
        out.put("latencyMs", o.latencyMillis());
        return ResponseEntity.status(o.status()).body(out);
    }

    @PostMapping("/api/v1/simulator/scenarios")
    public ResponseEntity<ScenarioView> register(@Valid @RequestBody CustomScenarioRequest req) {
        if (registry.builtIn(req.id())) throw new ResponseStatusException(HttpStatus.CONFLICT, "cannot overwrite built-in scenario " + req.id());
        BugScenario s;
        try { s = ScenarioFactory.create(req); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()); }
        registry.register(s);
        return ResponseEntity.status(HttpStatus.CREATED).body(view(s));
    }

    @GetMapping("/api/v1/simulator/scenarios")
    public List<ScenarioView> list() {
        return registry.all().stream().map(this::view).sorted(java.util.Comparator.comparing(ScenarioView::id)).toList();
    }

    @PostMapping("/api/v1/simulator/test")
    public Map<String, Object> test(@RequestBody TestRequest req) {
        BugScenario s = registry.find(req.scenarioId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown scenario"));
        Map<String, Object> in = req.input() == null ? Map.of() : req.input();
        ResponseObservation o = ScenarioEngine.run(s, in, Candidate.of(InputFlattener.flatten(in)).hash(), req.attempt() == null ? 0 : req.attempt());
        return Map.of("status", o.status(), "errorCode", o.errorCode() == null ? "" : o.errorCode(), "latencyMs", o.latencyMillis());
    }

    private ScenarioView view(BugScenario s) { return new ScenarioView(s.id(), s.description(), registry.builtIn(s.id()), s.expectedMinimalSets()); }
}
