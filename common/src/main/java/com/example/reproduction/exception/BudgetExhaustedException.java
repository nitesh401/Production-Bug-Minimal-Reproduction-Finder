package com.example.reproduction.exception;

public class BudgetExhaustedException extends ReproductionException {
    public enum Kind { EVALUATIONS, TIME }
    private final Kind kind;
    public BudgetExhaustedException(Kind kind, String message) { super(message); this.kind = kind; }
    public Kind kind() { return kind; }
}
