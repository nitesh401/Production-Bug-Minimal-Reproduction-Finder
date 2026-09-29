package com.example.reproduction.evaluation;

import com.example.reproduction.domain.Candidate;
import com.example.reproduction.domain.EvaluationResult;

/**
 * Runs ONE attempt of a candidate against the system under test.
 * Contract: return REPRODUCES_BUG / DOES_NOT_REPRODUCE only when the system actually answered;
 * for timeouts / connectivity problems return (or throw {@code EvaluationInfrastructureException} with)
 * TIMEOUT / SYSTEM_ERROR / INCONCLUSIVE. {@code attempt} is the 0-based attempt index (needed for
 * deterministic simulation of flaky bugs).
 */
public interface BugOracle {
    EvaluationResult evaluate(Candidate candidate, int attempt);
}
