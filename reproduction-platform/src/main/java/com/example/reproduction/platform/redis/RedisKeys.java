package com.example.reproduction.platform.redis;

import java.time.Duration;

/** One place for every Redis key so that all services agree on naming and TTLs. */
public final class RedisKeys {
    private RedisKeys() {}

    public static String progress(String jobId) { return "job:" + jobId + ":progress"; }        // hash
    public static String cancelled(String jobId) { return "job:" + jobId + ":cancelled"; }      // flag
    public static String context(String jobId) { return "job:" + jobId + ":context"; }          // JSON
    public static String evaluation(String key) { return "eval:" + key; }                       // JSON (namespace:hash)
    public static String taskLock(String taskId, int attempt) { return "task:" + taskId + ":" + attempt + ":lock"; }
    public static String taskResult(String taskId) { return "task:" + taskId + ":result"; }     // JSON
    public static String dedup(String scope, String id) { return "dedup:" + scope + ":" + id; }

    public static final Duration EVAL_TTL = Duration.ofHours(24);
    public static final Duration PROGRESS_TTL = Duration.ofDays(2);
    public static final Duration CONTEXT_TTL = Duration.ofHours(6);
    public static final Duration DEDUP_TTL = Duration.ofDays(7);
}
