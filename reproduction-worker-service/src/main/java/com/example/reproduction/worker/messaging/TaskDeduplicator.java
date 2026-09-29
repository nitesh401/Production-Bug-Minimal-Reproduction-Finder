package com.example.reproduction.worker.messaging;

import com.example.reproduction.messaging.TaskResult;
import com.example.reproduction.platform.redis.RedisKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Task ownership + idempotency via Redis:
 * - lock  task:{id}:{attempt}:lock  (SET NX EX lease): only one worker executes a given attempt;
 * - result task:{id}:result: a duplicate delivery of a FINISHED task re-publishes the stored result instead of recomputing.
 * If Redis is down we fail OPEN (execute anyway): duplicates then cost compute but never correctness, because the
 * orchestrator applies results idempotently and the evaluation cache/dominance make repeats cheap.
 */
@Component
public class TaskDeduplicator {
    private static final Logger log = LoggerFactory.getLogger(TaskDeduplicator.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public TaskDeduplicator(StringRedisTemplate redis, ObjectMapper mapper) { this.redis = redis; this.mapper = mapper; }

    public boolean tryAcquire(String taskId, int attempt, String workerId, Duration lease) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(RedisKeys.taskLock(taskId, attempt), workerId, lease));
        } catch (DataAccessException e) {
            log.warn("Redis unavailable: executing task without ownership lock ({})", e.toString());
            return true;
        }
    }

    public void release(String taskId, int attempt) {
        try { redis.delete(RedisKeys.taskLock(taskId, attempt)); } catch (DataAccessException ignored) { /* lease expires */ }
    }

    public Optional<TaskResult> finished(String taskId) {
        try {
            String json = redis.opsForValue().get(RedisKeys.taskResult(taskId));
            return json == null ? Optional.empty() : Optional.of(mapper.readValue(json, TaskResult.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void remember(TaskResult r) {
        try { redis.opsForValue().set(RedisKeys.taskResult(r.taskId()), mapper.writeValueAsString(r), Duration.ofHours(6)); }
        catch (Exception e) { log.warn("could not remember task result: {}", e.toString()); }
    }
}
