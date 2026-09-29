package com.example.reproduction.evaluation;

import com.example.reproduction.algorithm.search.DominanceIndex;
import com.example.reproduction.algorithm.search.SearchListener;
import com.example.reproduction.domain.*;
import com.example.reproduction.exception.EvaluationInfrastructureException;
import com.example.reproduction.exception.SearchCancelledException;

import java.util.BitSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Evaluates candidates with: dominance inference -> cache lookup -> in-flight de-duplication -> budget ->
 * multi-attempt flaky evaluation -> infra retries with back-off. Infrastructure failures are NEVER reported as
 * "does not reproduce" and are never cached.
 *
 * <p>Flakiness: a candidate is evaluated up to {@code evaluationAttempts} times; it reproduces iff
 * reproductions &gt;= ceil(minimumReproductionRate * attempts). Evaluation stops early as soon as the decision
 * is mathematically settled ({@link #confirm} runs all attempts to report a true probability).
 * Thread-safe.
 */
public final class CandidateEvaluator {
    public interface Sleeper { void sleep(long millis) throws InterruptedException; }

    private final FieldUniverse universe;
    private final BugOracle oracle;
    private final EvaluationCache cache;
    private final DominanceIndex dominance;
    private final SearchConfig config;
    private final EvaluationBudget budget;
    private final String namespace;
    private final EvaluationStats stats = new EvaluationStats();
    private final SearchListener listener;
    private final BooleanSupplier cancelled;
    private final Sleeper sleeper;
    private final ConcurrentHashMap<String, CompletableFuture<EvaluationResult>> inflight = new ConcurrentHashMap<>();

    public CandidateEvaluator(FieldUniverse universe, BugOracle oracle, EvaluationCache cache, DominanceIndex dominance,
                              SearchConfig config, EvaluationBudget budget, String namespace,
                              SearchListener listener, BooleanSupplier cancelled) {
        this(universe, oracle, cache, dominance, config, budget, namespace, listener, cancelled, Thread::sleep);
    }

    public CandidateEvaluator(FieldUniverse universe, BugOracle oracle, EvaluationCache cache, DominanceIndex dominance,
                              SearchConfig config, EvaluationBudget budget, String namespace,
                              SearchListener listener, BooleanSupplier cancelled, Sleeper sleeper) {
        this.universe = universe; this.oracle = oracle; this.cache = cache; this.dominance = dominance;
        this.config = config; this.budget = budget; this.namespace = namespace; this.listener = listener;
        this.cancelled = cancelled; this.sleeper = sleeper;
    }

    public EvaluationStats stats() { return stats; }
    public FieldUniverse universe() { return universe; }

    public EvaluationResult evaluate(BitSet fields) {
        if (cancelled.getAsBoolean()) throw new SearchCancelledException("search cancelled");
        Candidate candidate = universe.candidateOf(fields);
        String hash = candidate.hash();

        if (dominance != null && config.assumeMonotonic()) {
            EvaluationStatus inferred = dominance.infer(fields);
            if (inferred != null) {
                stats.dominanceInferences.incrementAndGet();
                EvaluationResult r = EvaluationResult.inferred(inferred, "dominance");
                listener.onEvaluation(fields, hash, r);
                return r;
            }
        }
        String key = namespace + ":" + hash;
        Optional<EvaluationResult> hit = cache.get(key);
        if (hit.isPresent()) {
            stats.cacheHits.incrementAndGet();
            EvaluationResult r = hit.get().withSource(EvaluationResult.Source.CACHE);
            record(fields, r);
            listener.onEvaluation(fields, hash, r);
            return r;
        }
        stats.cacheMisses.incrementAndGet();

        CompletableFuture<EvaluationResult> mine = new CompletableFuture<>();
        CompletableFuture<EvaluationResult> existing = inflight.putIfAbsent(key, mine);
        if (existing != null) { // another thread is already evaluating this exact candidate
            stats.inflightJoins.incrementAndGet();
            try { return existing.join(); }
            catch (CompletionException e) { throw e.getCause() instanceof RuntimeException re ? re : e; }
        }
        try {
            budget.acquire();
            stats.candidateEvaluations.incrementAndGet();
            EvaluationResult r = run(candidate, config.evaluationAttempts(), true);
            if (r.conclusive()) { cache.put(key, r); record(fields, r); }
            else stats.inconclusive.incrementAndGet();
            listener.onEvaluation(fields, hash, r);
            mine.complete(r);
            return r;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inflight.remove(key, mine);
        }
    }

    /** Runs ALL attempts (no early exit, no cache read) to obtain a real reproduction probability. */
    public EvaluationResult confirm(BitSet fields) {
        Candidate c = universe.candidateOf(fields);
        EvaluationResult r = run(c, config.evaluationAttempts(), false);
        listener.onEvaluation(fields, c.hash(), r);
        return r;
    }

    private void record(BitSet fields, EvaluationResult r) {
        if (dominance != null && config.assumeMonotonic()) dominance.record(fields, r.status());
    }

    private EvaluationResult run(Candidate c, int attempts, boolean earlyExit) {
        long t0 = System.nanoTime();
        int needed = (int) Math.ceil(config.minimumReproductionRate() * attempts - 1e-9);
        int reproduced = 0, notReproduced = 0, done = 0;
        for (int i = 0; i < attempts; i++) {
            EvaluationResult one = attemptWithRetries(c, i);
            if (!one.conclusive()) // infrastructure trouble => the whole candidate is INCONCLUSIVE/TIMEOUT/SYSTEM_ERROR
                return new EvaluationResult(one.status(), done, reproduced, done == 0 ? 0 : reproduced / (double) done,
                        millis(t0), one.detail(), EvaluationResult.Source.EVALUATED);
            done++;
            if (one.reproduces()) reproduced++; else notReproduced++;
            if (earlyExit && (reproduced >= needed || attempts - notReproduced < needed)) break;
        }
        double rate = reproduced / (double) done;
        EvaluationStatus s = reproduced >= needed ? EvaluationStatus.REPRODUCES_BUG : EvaluationStatus.DOES_NOT_REPRODUCE;
        return new EvaluationResult(s, done, reproduced, rate, millis(t0), reproduced + "/" + done, EvaluationResult.Source.EVALUATED);
    }

    private EvaluationResult attemptWithRetries(Candidate c, int attempt) {
        EvaluationResult last = null;
        for (int r = 0; r <= config.maxInfraRetries(); r++) {
            if (cancelled.getAsBoolean()) throw new SearchCancelledException("search cancelled");
            stats.oracleCalls.incrementAndGet();
            try {
                last = oracle.evaluate(c, attempt);
            } catch (EvaluationInfrastructureException e) {
                last = EvaluationResult.single(e.status(), 0, e.getMessage());
            }
            if (last.conclusive()) return last;
            if (r < config.maxInfraRetries()) {
                stats.infraRetries.incrementAndGet();
                try { sleeper.sleep(config.retryBackoffMillis() << Math.min(r, 6)); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new SearchCancelledException("interrupted"); }
            }
        }
        return last;
    }

    private static long millis(long t0) { return (System.nanoTime() - t0) / 1_000_000; }
}
