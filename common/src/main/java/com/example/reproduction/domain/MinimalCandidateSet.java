package com.example.reproduction.domain;

import java.util.List;

/** All discovered 1-minimal reproductions, best score first. There may be several. */
public record MinimalCandidateSet(List<MinimalCandidate> candidates) {
    public MinimalCandidate best() { return candidates.isEmpty() ? null : candidates.get(0); }
}
