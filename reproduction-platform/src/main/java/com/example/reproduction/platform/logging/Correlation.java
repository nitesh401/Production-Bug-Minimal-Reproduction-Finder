package com.example.reproduction.platform.logging;

import org.slf4j.MDC;

/** MDC keys used by the logging pattern in every service: correlationId, jobId, taskId, candidateId. */
public final class Correlation implements AutoCloseable {
    private Correlation() {}

    public static Correlation of(String correlationId, String jobId, String taskId, String candidateId) {
        put("correlationId", correlationId);
        put("jobId", jobId);
        put("taskId", taskId);
        put("candidateId", candidateId);
        return new Correlation();
    }

    private static void put(String k, String v) { if (v != null) MDC.put(k, v); else MDC.remove(k); }

    @Override public void close() { MDC.remove("correlationId"); MDC.remove("jobId"); MDC.remove("taskId"); MDC.remove("candidateId"); }
}
