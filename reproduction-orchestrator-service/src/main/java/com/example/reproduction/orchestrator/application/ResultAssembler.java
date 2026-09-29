package com.example.reproduction.orchestrator.application;

import com.example.reproduction.algorithm.graph.DefaultDependencyRules;
import com.example.reproduction.algorithm.graph.DependencyAnalysis;
import com.example.reproduction.algorithm.graph.DependencyAnalyzer;
import com.example.reproduction.algorithm.search.CandidateScorer;
import com.example.reproduction.domain.*;
import com.example.reproduction.messaging.TaskResult;

import java.math.BigInteger;
import java.util.*;

/** Pure function: merges task results into the final ranked {@link JobResult}. Unit-testable without Spring. */
public final class ResultAssembler {
    private ResultAssembler() {}

    public static JobResult assemble(FieldUniverse universe, List<Dependency> dependencies, SearchConfig cfg,
                                     List<TaskResult> taskResults, long candidatesEvaluated, boolean anyIncomplete, String note) {
        DependencyAnalysis analysis = new DependencyAnalyzer(DefaultDependencyRules.paymentDomain()).analyze(universe.paths(), dependencies);
        CandidateScorer scorer = new CandidateScorer(cfg.weights());
        Map<String, MinimalCandidate> distinct = new LinkedHashMap<>();
        long cacheHits = 0;
        for (TaskResult r : taskResults) {
            cacheHits += r.cacheHits();
            boolean usable = (r.outcome() == TaskResult.Outcome.SUCCESS || r.outcome() == TaskResult.Outcome.PARTIAL_BUDGET) && !r.minimalFields().isEmpty();
            if (!usable) continue;
            BitSet fields = universe.of(r.minimalFields());
            Candidate c = universe.candidateOf(fields);
            double confidence = r.outcome() == TaskResult.Outcome.SUCCESS ? r.reproductionRate() : 0.0;
            CandidateScore score = scorer.score(fields, universe, analysis.graph(), confidence, 0);
            boolean proven = r.provenMinimal() && r.outcome() == TaskResult.Outcome.SUCCESS;
            MinimalCandidate mc = new MinimalCandidate(universe.pathsOf(fields), c.hash(), c.nested(), confidence,
                    cfg.evaluationAttempts(), score, proven);
            distinct.merge(c.hash(), mc, (a, b) -> a.provenMinimal() ? a : b);
        }
        List<MinimalCandidate> ranked = new ArrayList<>(distinct.values());
        ranked.sort(Comparator.comparingDouble((MinimalCandidate m) -> m.score().total()).thenComparing(MinimalCandidate::candidateHash));
        if (ranked.size() > cfg.maxSolutions()) ranked = new ArrayList<>(ranked.subList(0, cfg.maxSolutions()));
        int best = ranked.isEmpty() ? universe.size() : ranked.get(0).fieldPaths().size();
        double reduction = universe.size() == 0 ? 0 : 100.0 * (universe.size() - best) / universe.size();
        boolean allProven = !ranked.isEmpty() && ranked.stream().allMatch(MinimalCandidate::provenMinimal) && !anyIncomplete;
        return new JobResult(universe.size(), BigInteger.TWO.pow(universe.size()).toString(), ranked, candidatesEvaluated, cacheHits, reduction,
                ranked.isEmpty() ? 0 : ranked.get(0).reproductionRate(), ranked.size(), allProven,
                anyIncomplete ? "INCOMPLETE" : "COMPLETED", note);
    }
}
