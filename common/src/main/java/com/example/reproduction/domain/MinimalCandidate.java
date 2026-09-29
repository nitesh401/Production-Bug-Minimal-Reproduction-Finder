package com.example.reproduction.domain;

import java.util.List;
import java.util.Map;

public record MinimalCandidate(List<String> fieldPaths, String candidateHash, Map<String, Object> input,
                               double reproductionRate, int confirmationAttempts, CandidateScore score,
                               boolean provenMinimal) {}
