package com.example.reproduction.worker.execution;

import com.example.reproduction.platform.redis.RedisKeys;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.function.BooleanSupplier;

/** Polls the job's cancel flag in Redis at most every 500 ms (evaluations may ask hundreds of times per second). */
public final class CancellationProbe implements BooleanSupplier {
    private final StringRedisTemplate redis;
    private final String key;
    private volatile long lastCheck = 0;
    private volatile boolean cancelled = false;

    public CancellationProbe(StringRedisTemplate redis, String jobId) { this.redis = redis; this.key = RedisKeys.cancelled(jobId); }

    @Override
    public boolean getAsBoolean() {
        if (cancelled) return true;
        long now = System.currentTimeMillis();
        if (now - lastCheck < 500) return false;
        lastCheck = now;
        try { cancelled = Boolean.TRUE.equals(redis.hasKey(key)); }
        catch (DataAccessException e) { /* Redis down: keep searching; orchestrator also enforces deadlines */ }
        return cancelled;
    }
}
