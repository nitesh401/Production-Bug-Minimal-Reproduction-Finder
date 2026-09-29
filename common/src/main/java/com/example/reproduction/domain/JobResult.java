package com.example.reproduction.domain;

import java.util.List;

/** Final (or partial) outcome of a job, stored as JSON on the job row and returned by GET .../result. */
public record JobResult(int originalFieldCount, String theoreticalSearchSpace, List<MinimalCandidate> minimalCandidates,
                        long candidatesEvaluated, long cacheHits, double reductionPercent, double reproductionConfidence,
                        int equivalentMinimalCandidates, boolean provenMinimal, String stopReason, String note) {}
