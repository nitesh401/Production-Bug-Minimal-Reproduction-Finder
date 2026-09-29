package com.example.reproduction.algorithm.search;

import com.example.reproduction.domain.MinimalCandidateSet;
import com.example.reproduction.domain.ReductionStep;
import com.example.reproduction.domain.StopReason;
import com.example.reproduction.evaluation.EvaluationStats;

import java.math.BigInteger;
import java.util.List;

public record SearchReport(int originalFieldCount, BigInteger theoreticalSearchSpace, MinimalCandidateSet minimal,
                           EvaluationStats.Snapshot stats, List<ReductionStep> steps,
                           List<ReductionOutcome.ProofLine> proof, StopReason stopReason,
                           long elapsedMillis, double reductionPercent) {}
