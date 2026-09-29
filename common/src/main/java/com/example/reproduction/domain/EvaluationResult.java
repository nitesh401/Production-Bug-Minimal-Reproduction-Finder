package com.example.reproduction.domain;

/** Aggregated result of evaluating a candidate (possibly over several attempts). */
public record EvaluationResult(EvaluationStatus status, int attempts, int reproductions,
                               double reproductionRate, long durationMillis, String detail, Source source) {

    public enum Source { EVALUATED, CACHE, INFERRED_BY_DOMINANCE }

    public boolean reproduces() { return status == EvaluationStatus.REPRODUCES_BUG; }

    public boolean conclusive() { return status.conclusive(); }

    public EvaluationResult withSource(Source s) {
        return new EvaluationResult(status, attempts, reproductions, reproductionRate, durationMillis, detail, s);
    }

    /** One attempt against the oracle. */
    public static EvaluationResult single(EvaluationStatus status, long millis, String detail) {
        int r = status == EvaluationStatus.REPRODUCES_BUG ? 1 : 0;
        int a = status.conclusive() ? 1 : 0;
        return new EvaluationResult(status, a, r, a == 0 ? 0 : r, millis, detail, Source.EVALUATED);
    }

    public static EvaluationResult inferred(EvaluationStatus status, String detail) {
        return new EvaluationResult(status, 0, status == EvaluationStatus.REPRODUCES_BUG ? 1 : 0,
                status == EvaluationStatus.REPRODUCES_BUG ? 1.0 : 0.0, 0, detail, Source.INFERRED_BY_DOMINANCE);
    }
}
