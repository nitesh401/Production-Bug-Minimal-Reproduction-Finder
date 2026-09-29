package com.example.reproduction.evaluation;

import com.example.reproduction.algorithm.search.DominanceIndex;
import com.example.reproduction.algorithm.search.SearchListener;
import com.example.reproduction.domain.*;
import com.example.reproduction.exception.BudgetExhaustedException;
import com.example.reproduction.exception.EvaluationInfrastructureException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CandidateEvaluatorTest {

    static FieldUniverse universe() { return new FieldUniverse(Map.of("a", 1, "b", 2, "c", 3)); }

    static CandidateEvaluator evaluator(BugOracle o, SearchConfig cfg, EvaluationCache cache, DominanceIndex dom) {
        return new CandidateEvaluator(universe(), o, cache, dom, cfg, new EvaluationBudget(cfg.maxEvaluations(), Duration.ofMinutes(1)),
                "ns", SearchListener.NOOP, () -> false, ms -> {});
    }

    static BitSet all() { BitSet b = new BitSet(); b.set(0, 3); return b; }

    static EvaluationResult r(EvaluationStatus s) { return EvaluationResult.single(s, 1, s.name()); }

    @Test
    void secondEvaluationOfSameCandidateHitsCacheAndSkipsOracle() {
        AtomicInteger calls = new AtomicInteger();
        CandidateEvaluator ev = evaluator((c, a) -> { calls.incrementAndGet(); return r(EvaluationStatus.REPRODUCES_BUG); },
                SearchConfig.defaults(), new InMemoryEvaluationCache(100), null);
        assertEquals(EvaluationResult.Source.EVALUATED, ev.evaluate(all()).source());
        assertEquals(EvaluationResult.Source.CACHE, ev.evaluate(all()).source());
        assertEquals(1, calls.get());
        assertEquals(1L, ev.stats().cacheHits.get());
        assertEquals(1L, ev.stats().cacheMisses.get());
    }

    @Test
    void timeoutIsInconclusiveNotCachedAndRetried() {
        AtomicInteger calls = new AtomicInteger();
        SearchConfig cfg = SearchConfig.builder().maxInfraRetries(2).build();
        InMemoryEvaluationCache cache = new InMemoryEvaluationCache(10);
        CandidateEvaluator ev = evaluator((c, a) -> { calls.incrementAndGet(); return r(EvaluationStatus.TIMEOUT); }, cfg, cache, null);
        EvaluationResult res = ev.evaluate(all());
        assertEquals(EvaluationStatus.TIMEOUT, res.status());
        assertFalse(res.status() == EvaluationStatus.DOES_NOT_REPRODUCE);
        assertEquals(3, calls.get()); // 1 + 2 retries
        assertEquals(0, cache.size());
        assertEquals(2L, ev.stats().infraRetries.get());
    }

    @Test
    void infrastructureExceptionIsRetriedAndThenRecovers() {
        AtomicInteger calls = new AtomicInteger();
        CandidateEvaluator ev = evaluator((c, a) -> {
            if (calls.incrementAndGet() < 3) throw new EvaluationInfrastructureException(EvaluationStatus.SYSTEM_ERROR, "simulator down", null);
            return r(EvaluationStatus.REPRODUCES_BUG);
        }, SearchConfig.builder().maxInfraRetries(3).build(), new InMemoryEvaluationCache(10), null);
        assertEquals(EvaluationStatus.REPRODUCES_BUG, ev.evaluate(all()).status());
    }

    @Test
    void flakyCandidateUsesReproductionRateThreshold() {
        List<EvaluationStatus> seq = List.of(EvaluationStatus.REPRODUCES_BUG, EvaluationStatus.REPRODUCES_BUG, EvaluationStatus.DOES_NOT_REPRODUCE);
        BugOracle oracle = (c, attempt) -> r(seq.get(attempt));
        SearchConfig loose = SearchConfig.builder().evaluationAttempts(3).minimumReproductionRate(0.66).build();
        EvaluationResult confirmed = evaluator(oracle, loose, new InMemoryEvaluationCache(10), null).confirm(all());
        assertEquals(2.0 / 3.0, confirmed.reproductionRate(), 1e-9);
        assertEquals(EvaluationStatus.REPRODUCES_BUG, confirmed.status());
        SearchConfig strict = SearchConfig.builder().evaluationAttempts(3).minimumReproductionRate(0.9).build();
        assertEquals(EvaluationStatus.DOES_NOT_REPRODUCE, evaluator(oracle, strict, new InMemoryEvaluationCache(10), null).confirm(all()).status());
    }

    @Test
    void earlyExitStopsOnceDecisionIsSettled() {
        AtomicInteger calls = new AtomicInteger();
        SearchConfig cfg = SearchConfig.builder().evaluationAttempts(5).minimumReproductionRate(0.6).build(); // needs 3
        evaluator((c, a) -> { calls.incrementAndGet(); return r(EvaluationStatus.REPRODUCES_BUG); }, cfg, new InMemoryEvaluationCache(10), null).evaluate(all());
        assertEquals(3, calls.get());
        calls.set(0);
        evaluator((c, a) -> { calls.incrementAndGet(); return r(EvaluationStatus.DOES_NOT_REPRODUCE); }, cfg, new InMemoryEvaluationCache(10), null).evaluate(all());
        assertEquals(3, calls.get()); // after 3 failures 5-3=2 < 3 needed: impossible
    }

    @Test
    void concurrentIdenticalEvaluationsShareOneOracleCall() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        CandidateEvaluator ev = evaluator((c, a) -> {
            calls.incrementAndGet();
            try { release.await(2, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return r(EvaluationStatus.REPRODUCES_BUG);
        }, SearchConfig.defaults(), new InMemoryEvaluationCache(10), null);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<EvaluationResult>> fs = new ArrayList<>();
        for (int i = 0; i < 6; i++) fs.add(pool.submit(() -> ev.evaluate(all())));
        Thread.sleep(200);
        release.countDown();
        for (Future<EvaluationResult> f : fs) assertEquals(EvaluationStatus.REPRODUCES_BUG, f.get().status());
        pool.shutdownNow();
        assertEquals(1, calls.get());
    }

    @Test
    void dominanceAvoidsEvaluatingSubsetsOfNonReproducingCandidates() {
        AtomicInteger calls = new AtomicInteger();
        DominanceIndex dom = new DominanceIndex();
        CandidateEvaluator ev = evaluator((c, a) -> { calls.incrementAndGet(); return r(EvaluationStatus.DOES_NOT_REPRODUCE); },
                SearchConfig.defaults(), new InMemoryEvaluationCache(10), dom);
        ev.evaluate(all());
        BitSet subset = new BitSet(); subset.set(0);
        EvaluationResult inferred = ev.evaluate(subset);
        assertEquals(EvaluationResult.Source.INFERRED_BY_DOMINANCE, inferred.source());
        assertEquals(1, calls.get());
    }

    @Test
    void evaluationBudgetIsEnforced() {
        SearchConfig cfg = SearchConfig.builder().maxEvaluations(1).build();
        CandidateEvaluator ev = evaluator((c, a) -> r(EvaluationStatus.DOES_NOT_REPRODUCE), cfg, new InMemoryEvaluationCache(10), null);
        ev.evaluate(all());
        BitSet other = new BitSet(); other.set(1);
        assertThrows(BudgetExhaustedException.class, () -> ev.evaluate(other));
    }
}
