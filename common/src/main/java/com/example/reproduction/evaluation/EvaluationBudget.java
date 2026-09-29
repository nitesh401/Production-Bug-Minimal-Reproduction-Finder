package com.example.reproduction.evaluation;

import com.example.reproduction.exception.BudgetExhaustedException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/** maxEvaluations + maxExecutionTime budget, shared by all threads working on one search. */
public final class EvaluationBudget {
    private final int maxEvaluations;
    private final long deadlineNanos;
    private final AtomicInteger used = new AtomicInteger();

    public EvaluationBudget(int maxEvaluations, Duration maxTime) {
        this.maxEvaluations = maxEvaluations;
        this.deadlineNanos = System.nanoTime() + maxTime.toNanos();
    }

    public void acquire() {
        if (System.nanoTime() > deadlineNanos) throw new BudgetExhaustedException(BudgetExhaustedException.Kind.TIME, "maxExecutionTime exceeded");
        if (used.incrementAndGet() > maxEvaluations) {
            used.decrementAndGet();
            throw new BudgetExhaustedException(BudgetExhaustedException.Kind.EVALUATIONS, "maxEvaluations (" + maxEvaluations + ") exceeded");
        }
    }

    public int used() { return used.get(); }
}
