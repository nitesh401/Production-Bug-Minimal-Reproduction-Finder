package com.example.reproduction.exception;

import com.example.reproduction.domain.EvaluationStatus;

public class InitialInputDoesNotReproduceException extends ReproductionException {
    private final EvaluationStatus status;
    public InitialInputDoesNotReproduceException(EvaluationStatus status) {
        super("The full input does not reproduce the bug (status=" + status + "); nothing to reduce");
        this.status = status;
    }
    public EvaluationStatus status() { return status; }
}
