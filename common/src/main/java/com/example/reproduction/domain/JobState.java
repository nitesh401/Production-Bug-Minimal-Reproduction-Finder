package com.example.reproduction.domain;

import java.util.EnumSet;
import java.util.Set;

/** State pattern: legal transitions are encoded once, next to the states. */
public enum JobState {
    PENDING, ANALYZING, SEARCHING, COMPLETED, PARTIAL, FAILED, CANCELLED, TIMED_OUT;

    public boolean terminal() { return this == COMPLETED || this == PARTIAL || this == FAILED || this == CANCELLED || this == TIMED_OUT; }

    public Set<JobState> next() {
        return switch (this) {
            case PENDING -> EnumSet.of(ANALYZING, CANCELLED, FAILED);
            case ANALYZING -> EnumSet.of(SEARCHING, CANCELLED, FAILED, TIMED_OUT, PENDING); // PENDING = crash recovery
            case SEARCHING -> EnumSet.of(COMPLETED, PARTIAL, CANCELLED, FAILED, TIMED_OUT);
            case FAILED, CANCELLED, TIMED_OUT, PARTIAL -> EnumSet.of(ANALYZING, SEARCHING); // resume
            case COMPLETED -> EnumSet.noneOf(JobState.class);
        };
    }

    public boolean canTransitionTo(JobState s) { return next().contains(s); }
}
