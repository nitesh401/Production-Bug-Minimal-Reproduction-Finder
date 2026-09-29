package com.example.reproduction.worker.execution;

import com.example.reproduction.messaging.JobContext;
import com.example.reproduction.platform.kafka.PoisonMessageException;
import com.example.reproduction.platform.redis.RedisKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

/** Loads a job's immutable context: Redis first, orchestrator REST as the source of truth (then re-cached). */
@Component
public class JobContextClient {
    private static final Logger log = LoggerFactory.getLogger(JobContextClient.class);
    private final StringRedisTemplate redis;
    private final WebClient orchestrator;
    private final ObjectMapper mapper;

    public JobContextClient(StringRedisTemplate redis, WebClient orchestratorWebClient, ObjectMapper mapper) {
        this.redis = redis; this.orchestrator = orchestratorWebClient; this.mapper = mapper;
    }

    public JobContext load(String jobId) {
        try {
            String cached = redis.opsForValue().get(RedisKeys.context(jobId));
            if (cached != null) return mapper.readValue(cached, JobContext.class);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable while loading job context, falling back to orchestrator: {}", e.toString());
        } catch (Exception e) {
            log.warn("Ignoring unreadable cached context: {}", e.toString());
        }
        try {
            JobContext ctx = orchestrator.get().uri("/internal/jobs/{id}/context", jobId).retrieve().bodyToMono(JobContext.class)
                    .timeout(Duration.ofSeconds(10)).block();
            if (ctx == null) throw new PoisonMessageException("empty context for job " + jobId);
            try { redis.opsForValue().set(RedisKeys.context(jobId), mapper.writeValueAsString(ctx), RedisKeys.CONTEXT_TTL); }
            catch (Exception e) { log.warn("Could not cache job context: {}", e.toString()); }
            return ctx;
        } catch (WebClientResponseException.NotFound e) {
            throw new PoisonMessageException("unknown job " + jobId);
        }
    }
}
