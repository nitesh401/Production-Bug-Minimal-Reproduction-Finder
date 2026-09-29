package com.example.reproduction.algorithm.search;

import com.example.reproduction.domain.EvaluationStatus;
import com.example.reproduction.domain.ReductionStep;
import com.example.reproduction.domain.StopReason;

import java.util.BitSet;
import java.util.List;

public record ReductionOutcome(BitSet fields, EvaluationStatus startStatus, boolean provenMinimal,
                               List<ReductionStep> steps, List<ProofLine> proof, StopReason stopReason, int iterations) {

    /** "Removing {@code removedFields} (and whatever depends on them) made the bug disappear: {@code verdict}." */
    public record ProofLine(List<String> removedFields, String verdict) {}

    public boolean startReproduced() { return startStatus == EvaluationStatus.REPRODUCES_BUG; }

    public static ReductionOutcome notReproducing(BitSet start, EvaluationStatus status) {
        return new ReductionOutcome(start, status, false, List.of(), List.of(), com.example.reproduction.domain.StopReason.COMPLETED, 0);
    }
}
