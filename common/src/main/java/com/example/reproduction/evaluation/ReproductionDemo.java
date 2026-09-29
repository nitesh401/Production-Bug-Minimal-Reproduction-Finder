package com.example.reproduction.evaluation;

import com.example.reproduction.algorithm.search.SearchReport;
import com.example.reproduction.domain.*;
import com.example.reproduction.simulator.*;

import java.text.NumberFormat;
import java.util.Locale;

/** Runs the pure core end to end without any infrastructure: {@code java -cp common.jar ...ReproductionDemo [SCENARIO] [STRATEGY]}. */
public final class ReproductionDemo {
    public static void main(String[] args) {
        String scenarioId = args.length > 0 ? args[0] : "SIMPLE_AND";
        StrategyType strategy = args.length > 1 ? StrategyType.valueOf(args[1]) : StrategyType.HYBRID;
        BugScenario scenario = Scenarios.builtIn().get(scenarioId);
        if (scenario == null) { System.err.println("Unknown scenario. Available: " + Scenarios.builtIn().keySet()); System.exit(2); }
        boolean flaky = scenario == Scenarios.FLAKY;
        BugSignature sig = scenario == Scenarios.DEPENDENCY_TRAP ? BugSignature.status(500) : BugSignature.statusAndCode(500, "PAYMENT_ROUTE_FAILURE");
        SearchConfig cfg = SearchConfig.builder().strategy(strategy).evaluationAttempts(flaky ? 5 : 3).minimumReproductionRate(flaky ? 0.6 : 0.66)
                .maxConcurrentEvaluations(4).maxSolutions(5).build();

        SearchReport r = SearchFacade.run(SamplePayloads.payment(), new SimulatorBugOracle(scenario, sig), cfg, new InMemoryEvaluationCache(100_000), "demo");
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        System.out.println("Scenario: " + scenario.description());
        System.out.println("Strategy: " + strategy);
        System.out.println("Original fields: " + r.originalFieldCount());
        System.out.println("Theoretical search space: " + nf.format(r.theoreticalSearchSpace()));
        System.out.println();
        System.out.println("Candidates evaluated: " + r.stats().candidateEvaluations() + " (oracle calls: " + r.stats().oracleCalls() + ")");
        System.out.println("Cache hits: " + r.stats().cacheHits() + "   Dominance-inferred: " + r.stats().dominanceInferences());
        System.out.println();
        MinimalCandidate best = r.minimal().best();
        System.out.println("Minimal reproduction (" + best.fieldPaths().size() + " fields): " + best.fieldPaths());
        System.out.printf("Reproduction confidence: %.0f%%   proven 1-minimal: %s%n", best.reproductionRate() * 100, best.provenMinimal());
        System.out.printf("Reduction: %.1f%%%n", r.reductionPercent());
        System.out.println("Equivalent minimal candidates: " + r.minimal().candidates().size());
        r.minimal().candidates().forEach(c -> System.out.println("  - " + c.fieldPaths() + "  score=" + String.format("%.2f", c.score().total())));
        System.out.println("Stop reason: " + r.stopReason() + "   elapsed: " + r.elapsedMillis() + " ms");
        System.out.println("Reduction steps:");
        r.steps().forEach(s -> System.out.println("  #" + s.index() + " [" + s.phase() + "/" + s.action() + "] " + s.sizeBefore() + " -> " + s.sizeAfter()));
    }
}
