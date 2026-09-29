package com.example.reproduction.evaluation;

import com.example.reproduction.domain.BugSignature;
import com.example.reproduction.domain.ResponseObservation;

import java.util.regex.Pattern;

/** Decides whether an observation matches the configured bug signature (all set criteria must match). */
public final class BugSignatureEvaluator {
    private final BugSignature sig;
    private final Pattern body;

    public BugSignatureEvaluator(BugSignature sig) {
        this.sig = sig;
        this.body = sig.bodyPattern() == null ? null : Pattern.compile(sig.bodyPattern());
    }

    public boolean matches(ResponseObservation o) {
        if (sig.httpStatus() != null && sig.httpStatus() != o.status()) return false;
        if (sig.errorCode() != null && !sig.errorCode().equals(o.errorCode())) return false;
        if (body != null && (o.body() == null || !body.matcher(o.body()).find())) return false;
        if (sig.minLatencyMillis() != null && o.latencyMillis() < sig.minLatencyMillis()) return false;
        if (sig.exceptionSignature() != null && (o.exception() == null || !o.exception().contains(sig.exceptionSignature()))) return false;
        return true;
    }
}
