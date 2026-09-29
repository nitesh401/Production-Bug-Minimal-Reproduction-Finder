package com.example.reproduction.algorithm.search;

import com.example.reproduction.algorithm.ddmin.UnitTester;
import com.example.reproduction.algorithm.ddmin.Verdict;
import com.example.reproduction.domain.EvaluationResult;
import com.example.reproduction.evaluation.CandidateEvaluator;
import com.example.reproduction.exception.SearchCancelledException;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Bridges ddmin (units) and the evaluator (fields). Batches run on a BOUNDED executor; results are consumed in
 * index order so the chosen candidate is always the lowest-index reproducing one (deterministic), while later
 * candidates are evaluated speculatively and cancelled when an earlier one already reproduces.
 */
public final class EvaluatorUnitTester implements UnitTester {
    private final UnitSpace space;
    private final CandidateEvaluator evaluator;
    private final ExecutorService executor; // nullable => sequential

    public EvaluatorUnitTester(UnitSpace space, CandidateEvaluator evaluator, ExecutorService executor) {
        this.space = space; this.evaluator = evaluator; this.executor = executor;
    }

    @Override
    public Verdict test(BitSet units, String purpose) {
        EvaluationResult r = evaluator.evaluate(space.expand(units));
        return switch (r.status()) {
            case REPRODUCES_BUG -> Verdict.REPRODUCES;
            case DOES_NOT_REPRODUCE -> Verdict.NOT_REPRODUCES;
            default -> Verdict.INCONCLUSIVE; // timeouts / infra errors are never "bug gone"
        };
    }

    @Override
    public BatchOutcome testFirst(List<BitSet> candidates, String purpose) {
        if (executor == null || candidates.size() < 2) return UnitTester.super.testFirst(candidates, purpose);
        List<Future<Verdict>> fs = submitAll(candidates, purpose);
        Verdict[] vs = new Verdict[candidates.size()];
        try {
            for (int i = 0; i < fs.size(); i++) {
                vs[i] = fs.get(i).get();
                if (vs[i] == Verdict.REPRODUCES) {
                    for (int j = i + 1; j < fs.size(); j++) fs.get(j).cancel(true);
                    return new BatchOutcome(i, vs);
                }
            }
            return new BatchOutcome(-1, vs);
        } catch (InterruptedException e) {
            cancelAll(fs);
            Thread.currentThread().interrupt();
            throw new SearchCancelledException("interrupted");
        } catch (ExecutionException e) {
            cancelAll(fs);
            throw unwrap(e);
        }
    }

    @Override
    public Verdict[] testAll(List<BitSet> candidates, String purpose) {
        if (executor == null || candidates.size() < 2) return UnitTester.super.testAll(candidates, purpose);
        List<Future<Verdict>> fs = submitAll(candidates, purpose);
        Verdict[] vs = new Verdict[fs.size()];
        try {
            for (int i = 0; i < vs.length; i++) vs[i] = fs.get(i).get();
            return vs;
        } catch (InterruptedException e) {
            cancelAll(fs);
            Thread.currentThread().interrupt();
            throw new SearchCancelledException("interrupted");
        } catch (ExecutionException e) {
            cancelAll(fs);
            throw unwrap(e);
        }
    }

    private List<Future<Verdict>> submitAll(List<BitSet> cs, String purpose) {
        List<Future<Verdict>> fs = new ArrayList<>(cs.size());
        for (BitSet c : cs) fs.add(executor.submit(() -> test(c, purpose)));
        return fs;
    }

    private static void cancelAll(List<Future<Verdict>> fs) { fs.forEach(f -> f.cancel(true)); }

    private static RuntimeException unwrap(ExecutionException e) {
        return e.getCause() instanceof RuntimeException re ? re : new IllegalStateException(e.getCause());
    }
}
