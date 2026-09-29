package com.example.reproduction.exception;

/** Root of all domain exceptions. Never catch plain Exception to "continue": catch these deliberately. */
public class ReproductionException extends RuntimeException {
    public ReproductionException(String message) { super(message); }
    public ReproductionException(String message, Throwable cause) { super(message, cause); }
}
