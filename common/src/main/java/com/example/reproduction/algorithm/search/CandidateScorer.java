package com.example.reproduction.algorithm.search;

import com.example.reproduction.algorithm.graph.DependencyGraph;
import com.example.reproduction.domain.*;

import java.util.BitSet;

/**
 * Lower is better:
 * total = wField*|fields| + wPayload*payloadBytes + wDep*dependencyEdgesInside
 *       + wCost*evaluationMillis + wConf*(1 - reproductionConfidence)
 * Field count dominates by default, then payload complexity, then dependency complexity; the confidence term
 * penalises candidates that reproduce only some of the time.
 */
public final class CandidateScorer {
    private final ScoringWeights w;
    public CandidateScorer(ScoringWeights w) { this.w = w; }

    public CandidateScore score(BitSet fields, FieldUniverse u, DependencyGraph g, double confidence, double costMillis) {
        int payload = u.candidateOf(fields).payloadSize();
        int edges = 0;
        for (int f = fields.nextSetBit(0); f >= 0; f = fields.nextSetBit(f + 1))
            for (int d : g.dependents(f)) if (fields.get(d)) edges++;
        int count = fields.cardinality();
        double total = w.fieldCount() * count + w.payloadSize() * payload + w.dependencyComplexity() * edges
                + w.evaluationCost() * costMillis + w.confidencePenalty() * (1.0 - confidence);
        return new CandidateScore(total, count, payload, edges, confidence, costMillis);
    }
}
