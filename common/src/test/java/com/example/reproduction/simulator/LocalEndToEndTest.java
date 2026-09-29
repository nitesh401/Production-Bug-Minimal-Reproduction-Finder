package com.example.reproduction.simulator;

import com.example.reproduction.algorithm.graph.*;
import com.example.reproduction.algorithm.search.*;
import com.example.reproduction.domain.*;
import com.example.reproduction.evaluation.*;
import com.example.reproduction.exception.BudgetExhaustedException;
import com.example.reproduction.exception.InitialInputDoesNotReproduceException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * In-JVM end to end: large input -> dependency analysis -> candidate generation -> parallel evaluation ->
 * caching / dominance -> reduction -> proven minimal reproduction. (The Kafka/Redis/MySQL variant is scripts/e2e.sh.)
 */
class LocalEndToEndTest {
    static final BugSignature SIG = BugSignature.statusAndCode(500, "PAYMENT_ROUTE_FAILURE");

    @Test
    void largeInputIsReducedToTheRealMinimum() {
        SearchConfig cfg = SearchConfig.builder().strategy(StrategyType.HYBRID).evaluationAttempts(3).minimumReproductionRate(0.66).build();
        InMemoryEvaluationCache cache = new InMemoryEvaluationCache(100_000);
        AtomicInteger oracleCalls = new AtomicInteger();
        BugOracle counting = (c, a) -> { oracleCalls.incrementAndGet(); return new SimulatorBugOracle(Scenarios.SIMPLE_AND, SIG).evaluate(c, a); };

        SearchReport r = SearchFacade.run(SamplePayloads.payment(), counting, cfg, cache, "e2e");
        assertEquals(27, r.originalFieldCount());
        assertEquals(java.math.BigInteger.TWO.pow(27), r.theoreticalSearchSpace());
        MinimalCandidate best = r.minimal().best();
        assertEquals(java.util.List.of("amount", "currency", "customerType", "featureFlags.FAST_PATH"), best.fieldPaths());
        assertTrue(best.provenMinimal());
        assertEquals(1.0, best.reproductionRate(), 1e-9);
        assertTrue(r.stats().candidateEvaluations() < 60, "candidates evaluated: " + r.stats().candidateEvaluations());
        assertTrue(r.stats().dominanceInferences() > 0);
        assertEquals(Map.of("amount", 15000, "currency", "INR", "customerType", "PREMIUM", "featureFlags", Map.of("FAST_PATH", true)), best.input());

        // second run over the same cache: candidates are served from cache instead of calling the oracle again.
        // (Not exactly zero: parallel speculation may have inferred some candidates in run 1 that run 2 evaluates.)
        int before = oracleCalls.get();
        SearchReport again = SearchFacade.run(SamplePayloads.payment(), counting, cfg, cache, "e2e");
        int second = oracleCalls.get() - before;
        assertTrue(second < before / 2, "second run oracle calls " + second + " vs first run " + before);
        assertTrue(again.stats().cacheHits() > 0);
        assertEquals(best.candidateHash(), again.minimal().best().candidateHash());
    }

    @Test
    void inputThatDoesNotReproduceIsRejectedNotSilentlyReduced() {
        Map<String, Object> harmless = Map.of("amount", 5, "currency", "INR");
        assertThrows(InitialInputDoesNotReproduceException.class,
                () -> SearchFacade.run(harmless, new SimulatorBugOracle(Scenarios.SIMPLE_AND, SIG), SearchConfig.defaults(), new InMemoryEvaluationCache(10), "x"));
    }

    @Test
    void tinyEvaluationBudgetYieldsPartialUnprovenResultInsteadOfFailing() {
        SearchConfig cfg = SearchConfig.builder().maxEvaluations(6).maxConcurrentEvaluations(1).build();
        SearchReport r = SearchFacade.run(SamplePayloads.payment(), new SimulatorBugOracle(Scenarios.SIMPLE_AND, SIG), cfg, new InMemoryEvaluationCache(1000), "b");
        assertEquals(StopReason.EVALUATION_BUDGET_EXHAUSTED, r.stopReason());
        assertFalse(r.minimal().best().provenMinimal());
        assertTrue(r.minimal().best().fieldPaths().size() < 27);
    }

    @Test
    void analysisFeedsDependencyAwareReduction() {
        FieldUniverse u = FieldUniverse.ofNested(SamplePayloads.payment());
        DependencyAnalysis a = new DependencyAnalyzer(DefaultDependencyRules.paymentDomain()).analyze(u.paths(), java.util.List.of());
        assertTrue(a.stronglyCoupledGroups().stream().anyMatch(g -> g.members().equals(java.util.List.of("amount", "currency"))));
        assertTrue(a.stronglyCoupledGroups().stream().anyMatch(g -> g.members().equals(java.util.List.of("shipping.city", "shipping.pincode"))));
        assertTrue(a.independentGroups().size() < u.size(), "dependencies merge singletons into groups");
    }
}
