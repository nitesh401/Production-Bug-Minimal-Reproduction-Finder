package com.example.reproduction.algorithm.search;

import com.example.reproduction.algorithm.graph.DependencyAnalysis;
import com.example.reproduction.domain.FieldUniverse;
import com.example.reproduction.domain.SearchConfig;
import com.example.reproduction.evaluation.CandidateEvaluator;

import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/** Everything one reduction run needs. Plain Java: no Spring, no I/O beyond the injected oracle. */
public record ReductionContext(FieldUniverse universe, DependencyAnalysis analysis, CandidateEvaluator evaluator,
                               SearchConfig config, BitSet startFields, BitSet bannedFields,
                               SearchListener listener, ExecutorService executor, Map<String, Double> keepHints) {

    public ReductionContext withBan(BitSet banned) {
        return new ReductionContext(universe, analysis, evaluator, config, startFields, banned, listener, executor, keepHints);
    }
}
