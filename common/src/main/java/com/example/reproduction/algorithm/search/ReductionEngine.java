package com.example.reproduction.algorithm.search;

import com.example.reproduction.algorithm.graph.DependencyAnalysis;
import com.example.reproduction.domain.*;
import com.example.reproduction.evaluation.*;
import com.example.reproduction.exception.InitialInputDoesNotReproduceException;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;

/**
 * Pure-Java orchestration of a complete local search: strategy selection, primary reduction, enumeration of
 * alternative minimal candidates, confirmation (real reproduction probability) and scoring.
 * The distributed system runs exactly these pieces, split into tasks (see the worker/orchestrator services).
 */
public final class ReductionEngine {

    public static ReductionStrategy strategyFor(StrategyType type) {
        return switch (type) {
            case DDMIN -> new DdminReductionStrategy();
            case DEPENDENCY_AWARE -> new DependencyAwareReductionStrategy();
            case PRIORITY -> new PriorityReductionStrategy();
            case HYBRID -> new HybridReductionStrategy();
        };
    }

    public SearchReport search(FieldUniverse universe, DependencyAnalysis analysis, BugOracle oracle, EvaluationCache cache,
                               SearchConfig cfg, String namespace, ExecutorService executor, SearchListener listener,
                               BooleanSupplier cancelled, Map<String, Double> keepHints) {
        long t0 = System.nanoTime();
        EvaluationBudget budget = new EvaluationBudget(cfg.maxEvaluations(), cfg.maxExecutionTime());
        CandidateEvaluator evaluator = new CandidateEvaluator(universe, oracle, cache, new DominanceIndex(), cfg, budget,
                namespace, listener, cancelled);
        ReductionStrategy strategy = strategyFor(cfg.strategy());
        BitSet none = new BitSet();
        ReductionContext ctx = new ReductionContext(universe, analysis, evaluator, cfg, universe.all(), none, listener, executor, keepHints);

        ReductionOutcome primary = strategy.reduce(ctx);
        if (!primary.startReproduced()) throw new InitialInputDoesNotReproduceException(primary.startStatus());

        Map<BitSet, ReductionOutcome> found = new LinkedHashMap<>();
        found.put(primary.fields(), primary);
        StopReason stop = primary.stopReason();

        // enumerate alternative minimal candidates
        Deque<BitSet> frontier = new ArrayDeque<>(MinimalEnumeration.nextBans(none, primary.fields()));
        Set<BitSet> visited = new HashSet<>(frontier);
        while (!frontier.isEmpty() && found.size() < cfg.maxSolutions() && stop == StopReason.COMPLETED) {
            BitSet ban = frontier.poll();
            ReductionOutcome o = strategy.reduce(ctx.withBan(ban));
            if (!o.startReproduced()) continue;
            if (o.stopReason() != StopReason.COMPLETED) { stop = o.stopReason(); if (!found.containsKey(o.fields())) found.put(o.fields(), o); break; }
            if (found.putIfAbsent(o.fields(), o) == null)
                for (BitSet nb : MinimalEnumeration.nextBans(ban, o.fields())) if (visited.add(nb)) frontier.add(nb);
        }

        CandidateScorer scorer = new CandidateScorer(cfg.weights());
        List<MinimalCandidate> minimal = new ArrayList<>();
        for (ReductionOutcome o : found.values()) {
            EvaluationResult conf = evaluator.confirm(o.fields());
            Candidate c = universe.candidateOf(o.fields());
            CandidateScore score = scorer.score(o.fields(), universe, analysis.graph(), conf.reproductionRate(), conf.durationMillis());
            minimal.add(new MinimalCandidate(universe.pathsOf(o.fields()), c.hash(), c.nested(), conf.reproductionRate(),
                    conf.attempts(), score, o.provenMinimal() && conf.reproduces()));
        }
        minimal.sort(Comparator.comparingDouble((MinimalCandidate m) -> m.score().total()).thenComparing(MinimalCandidate::candidateHash));

        int best = minimal.isEmpty() ? universe.size() : minimal.get(0).fieldPaths().size();
        double reduction = universe.size() == 0 ? 0 : 100.0 * (universe.size() - best) / universe.size();
        return new SearchReport(universe.size(), BigInteger.TWO.pow(universe.size()), new MinimalCandidateSet(minimal),
                evaluator.stats().snapshot(), primary.steps(), primary.proof(), stop,
                (System.nanoTime() - t0) / 1_000_000, reduction);
    }
}
