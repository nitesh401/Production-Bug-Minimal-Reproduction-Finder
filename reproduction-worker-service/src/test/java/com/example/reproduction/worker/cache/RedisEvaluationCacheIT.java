package com.example.reproduction.worker.cache;

import com.example.reproduction.domain.EvaluationResult;
import com.example.reproduction.domain.EvaluationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.*;

/** Needs Docker. Runs with `mvn verify` (failsafe), not with `mvn test`. */
@Testcontainers
class RedisEvaluationCacheIT {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static StringRedisTemplate template(int port) {
        LettuceConnectionFactory f = new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", port));
        f.afterPropertiesSet();
        StringRedisTemplate t = new StringRedisTemplate(f);
        t.afterPropertiesSet();
        return t;
    }

    static final EvaluationResult R = new EvaluationResult(EvaluationStatus.REPRODUCES_BUG, 3, 3, 1.0, 12, "3/3", EvaluationResult.Source.EVALUATED);

    @Test
    void resultWrittenByOneWorkerIsVisibleToAnotherThroughRedis() {
        StringRedisTemplate redis = template(REDIS.getMappedPort(6379));
        RedisEvaluationCache workerA = new RedisEvaluationCache(redis, new ObjectMapper(), new SimpleMeterRegistry(), 10);
        RedisEvaluationCache workerB = new RedisEvaluationCache(redis, new ObjectMapper(), new SimpleMeterRegistry(), 10);
        workerA.put("ns:hash1", R);
        var hit = workerB.get("ns:hash1");
        assertTrue(hit.isPresent());
        assertEquals(EvaluationStatus.REPRODUCES_BUG, hit.get().status());
        assertTrue(workerB.get("ns:other").isEmpty());
    }

    @Test
    void redisOutageDegradesToMissInsteadOfFailingTheSearch() {
        StringRedisTemplate redis = template(1); // nothing listens on port 1
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        RedisEvaluationCache cache = new RedisEvaluationCache(redis, new ObjectMapper(), meters, 10);
        assertTrue(cache.get("ns:x").isEmpty());
        cache.put("ns:x", R);                       // must not throw
        assertTrue(cache.get("ns:x").isPresent());  // L1 still works
        assertTrue(meters.get("redis_unavailable").counter().count() >= 1);
    }
}
