package com.example.reproduction.evaluation;

import com.example.reproduction.domain.EvaluationResult;

import java.util.Optional;

/** Cache of CONCLUSIVE evaluation results keyed by namespaced candidate hash. */
public interface EvaluationCache {
    Optional<EvaluationResult> get(String key);
    void put(String key, EvaluationResult result);
}
