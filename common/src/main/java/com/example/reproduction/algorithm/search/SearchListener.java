package com.example.reproduction.algorithm.search;

import com.example.reproduction.domain.EvaluationResult;
import com.example.reproduction.domain.ReductionStep;

import java.util.BitSet;

/** Observer: workers use it to publish evaluations to Kafka and to update metrics. All methods are no-ops by default. */
public interface SearchListener {
    default void onEvaluation(BitSet fields, String candidateHash, EvaluationResult result) {}
    default void onStep(ReductionStep step) {}

    SearchListener NOOP = new SearchListener() {};
}
