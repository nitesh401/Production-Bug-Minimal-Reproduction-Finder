package com.example.reproduction.evaluation;

import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe counters; {@link #snapshot()} is what reports and metrics read. */
public final class EvaluationStats {
    public final AtomicLong candidateEvaluations = new AtomicLong(); // distinct candidates sent to the oracle
    public final AtomicLong oracleCalls = new AtomicLong();          // individual attempts (incl. retries)
    public final AtomicLong cacheHits = new AtomicLong();
    public final AtomicLong cacheMisses = new AtomicLong();
    public final AtomicLong dominanceInferences = new AtomicLong();
    public final AtomicLong inflightJoins = new AtomicLong();
    public final AtomicLong infraRetries = new AtomicLong();
    public final AtomicLong inconclusive = new AtomicLong();

    public record Snapshot(long candidateEvaluations, long oracleCalls, long cacheHits, long cacheMisses,
                           long dominanceInferences, long inflightJoins, long infraRetries, long inconclusive) {}

    public Snapshot snapshot() {
        return new Snapshot(candidateEvaluations.get(), oracleCalls.get(), cacheHits.get(), cacheMisses.get(),
                dominanceInferences.get(), inflightJoins.get(), infraRetries.get(), inconclusive.get());
    }
}
