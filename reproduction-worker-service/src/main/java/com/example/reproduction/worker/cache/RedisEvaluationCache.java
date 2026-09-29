package com.example.reproduction.worker.cache;

import com.example.reproduction.domain.EvaluationResult;
import com.example.reproduction.evaluation.EvaluationCache;
import com.example.reproduction.evaluation.InMemoryEvaluationCache;
import com.example.reproduction.platform.redis.RedisKeys;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

/**
 * Two-tier cache: L1 in-process LRU, L2 Redis (shared by all workers and all jobs with the same namespace).
 * Redis is an OPTIMISATION, not a dependency: any Redis failure degrades to "miss" / "skip write" and is counted,
 * the search continues correctly (just with more oracle calls).
 */
public final class RedisEvaluationCache implements EvaluationCache {
    private static final Logger log = LoggerFactory.getLogger(RedisEvaluationCache.class);
    private final InMemoryEvaluationCache l1;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Counter redisFailures;

    public RedisEvaluationCache(StringRedisTemplate redis, ObjectMapper mapper, MeterRegistry meters, int l1Size) {
        this.l1 = new InMemoryEvaluationCache(l1Size);
        this.redis = redis; this.mapper = mapper;
        this.redisFailures = Counter.builder("redis_unavailable").description("Redis operations that failed and were degraded").register(meters);
    }

    @Override
    public Optional<EvaluationResult> get(String key) {
        Optional<EvaluationResult> hit = l1.get(key);
        if (hit.isPresent()) return hit;
        try {
            String json = redis.opsForValue().get(RedisKeys.evaluation(key));
            if (json == null) return Optional.empty();
            EvaluationResult r = mapper.readValue(json, EvaluationResult.class);
            l1.put(key, r);
            return Optional.of(r);
        } catch (DataAccessException e) {
            degraded("get", e);
            return Optional.empty();
        } catch (JsonProcessingException e) {
            log.warn("Corrupt cache entry {} ignored: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, EvaluationResult result) {
        l1.put(key, result);
        try {
            redis.opsForValue().set(RedisKeys.evaluation(key), mapper.writeValueAsString(result), RedisKeys.EVAL_TTL);
        } catch (DataAccessException e) {
            degraded("put", e);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialise evaluation result", e);
        }
    }

    private void degraded(String op, Exception e) {
        redisFailures.increment();
        log.warn("Redis {} failed, continuing without L2 cache: {}", op, e.toString());
    }
}
