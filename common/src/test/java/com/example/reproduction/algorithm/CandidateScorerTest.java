package com.example.reproduction.algorithm;

import com.example.reproduction.algorithm.graph.DependencyGraph;
import com.example.reproduction.algorithm.search.CandidateScorer;
import com.example.reproduction.domain.*;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CandidateScorerTest {
    final FieldUniverse u = new FieldUniverse(Map.of("a", 1, "b", 2, "c", "some longer string value", "d", 4));
    final CandidateScorer scorer = new CandidateScorer(ScoringWeights.defaults());
    final DependencyGraph g = DependencyGraph.of(4, List.of(new int[]{0, 1}));

    static BitSet bits(int... i) { BitSet b = new BitSet(); for (int x : i) b.set(x); return b; }

    @Test
    void fewerFieldsScoreBetter() {
        assertTrue(scorer.score(bits(0), u, g, 1.0, 0).total() < scorer.score(bits(0, 1), u, g, 1.0, 0).total());
    }

    @Test
    void lowerConfidenceIsPenalised() {
        assertTrue(scorer.score(bits(0), u, g, 1.0, 0).total() < scorer.score(bits(0), u, g, 0.66, 0).total());
    }

    @Test
    void dependencyEdgesInsideCandidateAreCounted() {
        assertEquals(1, scorer.score(bits(0, 1), u, g, 1.0, 0).dependencyEdges());
        assertEquals(0, scorer.score(bits(0, 2), u, g, 1.0, 0).dependencyEdges());
    }

    @Test
    void payloadBreaksTiesBetweenSameSizeCandidates() {
        assertTrue(scorer.score(bits(0), u, g, 1.0, 0).total() < scorer.score(bits(2), u, g, 1.0, 0).total());
    }
}
