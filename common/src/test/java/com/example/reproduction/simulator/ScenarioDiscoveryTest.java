package com.example.reproduction.simulator;

import com.example.reproduction.algorithm.search.SearchReport;
import com.example.reproduction.domain.*;
import com.example.reproduction.evaluation.*;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** The algorithm must DISCOVER each scenario's minimal sets; nothing is hard-coded. */
class ScenarioDiscoveryTest {

    static SearchReport run(BugScenario s, BugSignature sig, StrategyType st, int attempts, double rate, int concurrency) {
        SearchConfig cfg = SearchConfig.builder().strategy(st).evaluationAttempts(attempts).minimumReproductionRate(rate)
                .maxConcurrentEvaluations(concurrency).build();
        return SearchFacade.run(SamplePayloads.payment(), new SimulatorBugOracle(s, sig), cfg, new InMemoryEvaluationCache(100_000), "t-" + s.id());
    }

    static Set<Set<String>> found(SearchReport r) {
        return r.minimal().candidates().stream().map(c -> new HashSet<>(c.fieldPaths())).collect(Collectors.toSet());
    }

    static final BugSignature CODE = BugSignature.statusAndCode(500, "PAYMENT_ROUTE_FAILURE");

    @Test
    void scenario1_simpleConjunction() {
        SearchReport r = run(Scenarios.SIMPLE_AND, CODE, StrategyType.HYBRID, 3, 0.66, 4);
        assertEquals(new HashSet<>(Scenarios.SIMPLE_AND.expectedMinimalSets()), found(r));
        assertTrue(r.minimal().best().provenMinimal());
        assertEquals(1.0, r.minimal().best().reproductionRate(), 1e-9);
        assertTrue(r.stats().candidateEvaluations() < 100, "evaluations: " + r.stats().candidateEvaluations());
        assertTrue(r.reductionPercent() > 80);
    }

    @Test
    void scenario2_orBranchYieldsTwoMinimalCandidates() {
        SearchReport r = run(Scenarios.OR_BRANCH, CODE, StrategyType.HYBRID, 1, 1.0, 4);
        assertEquals(new HashSet<>(Scenarios.OR_BRANCH.expectedMinimalSets()), found(r));
        assertEquals(2, r.minimal().candidates().size());
    }

    @Test
    void scenario3_nestedJson() {
        SearchReport r = run(Scenarios.NESTED_JSON, CODE, StrategyType.HYBRID, 1, 1.0, 4);
        assertEquals(new HashSet<>(Scenarios.NESTED_JSON.expectedMinimalSets()), found(r));
    }

    @Test
    void scenario4_dependencyTrap_plainDdminIsFooled_dependencyAwareIsNot() {
        SearchReport naive = run(Scenarios.DEPENDENCY_TRAP, BugSignature.status(500), StrategyType.DDMIN, 1, 1.0, 1);
        Set<String> spurious = new HashSet<>(naive.minimal().best().fieldPaths());
        assertEquals(1, spurious.size(), "plain ddmin converges on a malformed 1-field input: " + spurious);
        assertFalse(found(naive).contains(Scenarios.DEPENDENCY_TRAP.expectedMinimalSets().get(0)));

        for (StrategyType st : new StrategyType[]{StrategyType.DEPENDENCY_AWARE, StrategyType.PRIORITY, StrategyType.HYBRID}) {
            SearchReport aware = run(Scenarios.DEPENDENCY_TRAP, BugSignature.status(500), st, 1, 1.0, 4);
            assertEquals(new HashSet<>(Scenarios.DEPENDENCY_TRAP.expectedMinimalSets()), found(aware), st.name());
        }
    }

    @Test
    void scenario5_flakyBugIsReducedWithReproductionRate() {
        SearchReport r = run(Scenarios.FLAKY, CODE, StrategyType.HYBRID, 5, 0.6, 4);
        assertEquals(new HashSet<>(Scenarios.FLAKY.expectedMinimalSets()), found(r));
        double rate = r.minimal().best().reproductionRate();
        assertTrue(rate >= 0.6 && rate <= 1.0, "rate " + rate);
    }

    @Test
    void scenario6_multipleIndependentMinimalSets() {
        SearchReport r = run(Scenarios.MULTI_MINIMAL, CODE, StrategyType.HYBRID, 1, 1.0, 4);
        assertEquals(new HashSet<>(Scenarios.MULTI_MINIMAL.expectedMinimalSets()), found(r));
        assertEquals(2, r.minimal().best().fieldPaths().size()); // best-scored (fewest fields) first
    }

    @Test
    void allStrategiesAgreeOnDeterministicScenarios() {
        for (StrategyType st : StrategyType.values()) {
            SearchReport r = run(Scenarios.SIMPLE_AND, CODE, st, 1, 1.0, 2);
            assertEquals(new HashSet<>(Scenarios.SIMPLE_AND.expectedMinimalSets()), found(r), st.name());
        }
    }

    @Test
    void parallelAndSequentialSearchesFindTheSameResult() {
        SearchReport seq = run(Scenarios.OR_BRANCH, CODE, StrategyType.HYBRID, 1, 1.0, 1);
        SearchReport par = run(Scenarios.OR_BRANCH, CODE, StrategyType.HYBRID, 1, 1.0, 8);
        assertEquals(found(seq), found(par));
        assertEquals(seq.minimal().best().candidateHash(), par.minimal().best().candidateHash());
    }
}
