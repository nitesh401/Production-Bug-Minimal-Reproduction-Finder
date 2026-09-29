package com.example.reproduction.orchestrator.application;

import com.example.reproduction.exception.ReproductionException;

public class IllegalJobStateException extends ReproductionException {
    public IllegalJobStateException(String message) { super(message); }
}
