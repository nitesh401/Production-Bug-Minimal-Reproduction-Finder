package com.example.reproduction.exception;

import com.example.reproduction.domain.EvaluationStatus;

/** The oracle could not give an answer (timeout, connection refused, 5xx from a proxy...). Retryable. */
public class EvaluationInfrastructureException extends ReproductionException {
    private final EvaluationStatus status;
    public EvaluationInfrastructureException(EvaluationStatus status, String message, Throwable cause) {
        super(message, cause);
        if (status.conclusive()) throw new IllegalArgumentException("infrastructure status must be non-conclusive");
        this.status = status;
    }
    public EvaluationStatus status() { return status; }
}
