package com.example.reproduction.evaluation;

import com.example.reproduction.algorithm.graph.DefaultDependencyRules;
import com.example.reproduction.algorithm.graph.DependencyAnalysis;
import com.example.reproduction.algorithm.graph.DependencyAnalyzer;
import com.example.reproduction.algorithm.search.ParallelExecutors;
import com.example.reproduction.algorithm.search.ReductionEngine;
import com.example.reproduction.algorithm.search.SearchListener;
import com.example.reproduction.algorithm.search.SearchReport;
import com.example.reproduction.domain.*;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/** Convenience wiring of the pure core for tests, the demo and the CLI. */
public final class SearchFacade {
    private SearchFacade() {}

    public static SearchReport run(Map<String, Object> nestedInput, BugOracle oracle, SearchConfig cfg, EvaluationCache cache, String namespace) {
        FieldUniverse universe = FieldUniverse.ofNested(nestedInput);
        DependencyAnalysis analysis = new DependencyAnalyzer(DefaultDependencyRules.paymentDomain()).analyze(universe.paths(), java.util.List.of());
        ThreadPoolExecutor pool = cfg.maxConcurrentEvaluations() > 1 ? ParallelExecutors.bounded(cfg.maxConcurrentEvaluations(), "eval") : null;
        try {
            return new ReductionEngine().search(universe, analysis, oracle, cache, cfg, namespace, pool, SearchListener.NOOP, () -> false, Map.of());
        } finally {
            if (pool != null) pool.shutdownNow();
        }
    }
}
