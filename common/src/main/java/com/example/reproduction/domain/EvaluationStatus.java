package com.example.reproduction.domain;

/**
 * Outcome of evaluating one candidate. Only the first two are conclusive statements about the bug;
 * the other three are infrastructure/uncertainty states and must NEVER be interpreted as
 * "the bug disappeared".
 */
public enum EvaluationStatus {
    REPRODUCES_BUG, DOES_NOT_REPRODUCE, INCONCLUSIVE, TIMEOUT, SYSTEM_ERROR;

    public boolean conclusive() {
        return this == REPRODUCES_BUG || this == DOES_NOT_REPRODUCE;
    }
}
