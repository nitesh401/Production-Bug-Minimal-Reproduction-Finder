package com.example.reproduction.messaging;

public final class Topics {
    private Topics() {}
    /** Commands from the API: SUBMIT / CANCEL / RESUME. Key = jobId (per-job ordering). */
    public static final String JOBS = "reproduction.jobs";
    /** Analysis history events (orchestrator -> analysis). Key = jobId. */
    public static final String ANALYSIS = "reproduction.analysis";
    /** Search tasks for workers. Key = taskId so parallel tasks of ONE job spread over partitions. */
    public static final String CANDIDATES = "reproduction.candidates";
    /** Re-enqueued tasks (attempt > 1). Same payload, same worker listener. */
    public static final String CANDIDATES_RETRY = "reproduction.candidates.retry";
    /** One event per candidate evaluation (audit + progress). Key = jobId. */
    public static final String EVALUATIONS = "reproduction.evaluations";
    /** Task results. Key = jobId. */
    public static final String RESULTS = "reproduction.results";
    /** Poison / exhausted messages. */
    public static final String DLQ = "reproduction.dlq";
}
