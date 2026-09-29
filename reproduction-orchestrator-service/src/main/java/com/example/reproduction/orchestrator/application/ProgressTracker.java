package com.example.reproduction.orchestrator.application;

import com.example.reproduction.platform.redis.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/** Short-lived progress in a Redis hash. Best effort: MySQL stays the source of truth, Redis loss only blurs progress. */
@Component
public class ProgressTracker {
    private static final Logger log = LoggerFactory.getLogger(ProgressTracker.class);
    private final StringRedisTemplate redis;

    public ProgressTracker(StringRedisTemplate redis) { this.redis = redis; }

    public void set(String jobId, Map<String, String> values) {
        try {
            var ops = redis.opsForHash();
            values.forEach((k, v) -> ops.put(RedisKeys.progress(jobId), k, v));
            ops.put(RedisKeys.progress(jobId), "updatedAt", Instant.now().toString());
            redis.expire(RedisKeys.progress(jobId), RedisKeys.PROGRESS_TTL);
        } catch (DataAccessException e) { log.warn("progress update skipped (Redis): {}", e.toString()); }
    }

    public void increment(String jobId, String field, long delta) {
        try {
            redis.opsForHash().increment(RedisKeys.progress(jobId), field, delta);
            redis.expire(RedisKeys.progress(jobId), RedisKeys.PROGRESS_TTL);
        } catch (DataAccessException e) { log.warn("progress increment skipped (Redis): {}", e.toString()); }
    }

    public void setCancelled(String jobId, boolean cancelled) {
        try {
            if (cancelled) redis.opsForValue().set(RedisKeys.cancelled(jobId), "1", RedisKeys.PROGRESS_TTL);
            else redis.delete(RedisKeys.cancelled(jobId));
        } catch (DataAccessException e) { log.warn("cancel flag not written (Redis down): workers rely on task deadlines. {}", e.toString()); }
    }
}
