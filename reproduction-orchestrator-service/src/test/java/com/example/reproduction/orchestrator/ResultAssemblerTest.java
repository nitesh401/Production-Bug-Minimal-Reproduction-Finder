package com.example.reproduction.orchestrator;

import com.example.reproduction.domain.*;
import com.example.reproduction.messaging.TaskResult;
import com.example.reproduction.orchestrator.application.ResultAssembler;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResultAssemblerTest {
    final FieldUniverse universe = FieldUniverse.ofNested(Map.of("a", 1, "b", 2, "c", 3, "d", 4, "e", 5, "f", 6));
    final SearchConfig cfg = SearchConfig.builder().maxSolutions(3).build();

    static TaskResult result(TaskResult.Outcome o, List<String> min, boolean proven, double rate) {
        return new TaskResult("j", "t" + min, "c", "h", 1, Instant.now(), "corr", o, min, proven, rate, List.of(), List.of(), StopReason.COMPLETED, "", 10, 3);
    }

    @Test
    void ranksSmallestFirstAndDeduplicatesEquivalentResults() {
        JobResult r = ResultAssembler.assemble(universe, List.of(), cfg, List.of(
                result(TaskResult.Outcome.SUCCESS, List.of("a", "b", "c"), true, 1.0),
                result(TaskResult.Outcome.SUCCESS, List.of("d", "e"), true, 1.0),
                result(TaskResult.Outcome.SUCCESS, List.of("d", "e"), true, 1.0),
                result(TaskResult.Outcome.START_NOT_REPRODUCING, List.of(), false, 0)), 30, false, null);
        assertEquals(2, r.equivalentMinimalCandidates());
        assertEquals(List.of("d", "e"), r.minimalCandidates().get(0).fieldPaths());
        assertEquals(6, r.originalFieldCount());
        assertEquals("64", r.theoreticalSearchSpace());
        assertEquals(66.7, r.reductionPercent(), 0.1);
        assertTrue(r.provenMinimal());
        assertEquals(12L, r.cacheHits()); // 4 task results x 3
    }

    @Test
    void partialResultsAreNeverReportedAsProven() {
        JobResult r = ResultAssembler.assemble(universe, List.of(), cfg,
                List.of(result(TaskResult.Outcome.PARTIAL_BUDGET, List.of("a", "b", "c", "d"), true, 0)), 5, true, "partial");
        assertFalse(r.provenMinimal());
        assertEquals("INCOMPLETE", r.stopReason());
        assertFalse(r.minimalCandidates().get(0).provenMinimal());
    }

    @Test
    void truncatesToMaxSolutions() {
        JobResult r = ResultAssembler.assemble(universe, List.of(), SearchConfig.builder().maxSolutions(1).build(), List.of(
                result(TaskResult.Outcome.SUCCESS, List.of("a"), true, 1.0), result(TaskResult.Outcome.SUCCESS, List.of("b"), true, 1.0)), 2, false, null);
        assertEquals(1, r.minimalCandidates().size());
    }
}
