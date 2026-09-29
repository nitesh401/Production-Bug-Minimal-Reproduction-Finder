package com.example.reproduction.orchestrator.application;

import com.example.reproduction.domain.BugSignature;
import com.example.reproduction.domain.Dependency;
import com.example.reproduction.domain.JobOptions;
import com.example.reproduction.exception.ReproductionException;
import com.example.reproduction.messaging.JobContext;
import com.example.reproduction.persistence.entity.*;
import com.example.reproduction.persistence.repository.*;
import com.example.reproduction.platform.redis.RedisKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class JobContextService {
    public static class JobNotFoundException extends ReproductionException {
        public JobNotFoundException(String id) { super("job not found: " + id); }
    }

    private final ReproductionJobRepository jobs;
    private final BugSignatureRepository signatures;
    private final InputFieldRepository fields;
    private final InputDependencyRepository deps;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public JobContextService(ReproductionJobRepository jobs, BugSignatureRepository signatures, InputFieldRepository fields,
                             InputDependencyRepository deps, StringRedisTemplate redis, ObjectMapper mapper) {
        this.jobs = jobs; this.signatures = signatures; this.fields = fields; this.deps = deps; this.redis = redis; this.mapper = mapper;
    }

    /** Redis copy (contains priority hints) if present, otherwise rebuilt from MySQL (source of truth). */
    @Transactional(readOnly = true)
    public JobContext get(String jobId) {
        try {
            String cached = redis.opsForValue().get(RedisKeys.context(jobId));
            if (cached != null) return mapper.readValue(cached, JobContext.class);
        } catch (DataAccessException | java.io.IOException ignored) { /* rebuild from DB */ }
        return build(jobId, Map.of());
    }

    @Transactional(readOnly = true)
    public JobContext build(String jobId, Map<String, Double> hints) {
        ReproductionJobEntity job = jobs.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        BugSignatureEntity s = signatures.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        try {
            Map<String, Object> input = mapper.readValue(job.initialInputJson, new TypeReference<Map<String, Object>>() {});
            JobOptions options = mapper.readValue(job.optionsJson, JobOptions.class);
            List<InputFieldEntity> fs = fields.findByJobIdOrderByIdx(jobId);
            List<Dependency> dependencies = deps.findByJobId(jobId).stream()
                    .map(d -> new Dependency(fs.get(d.fromIdx).path, fs.get(d.toIdx).path, d.reason)).toList();
            BugSignature sig = new BugSignature(s.httpStatus, s.errorCode, s.bodyPattern, s.minLatencyMs, s.exceptionSignature);
            return new JobContext(jobId, input, sig, options.toSearchConfig(), job.scenarioId, dependencies, hints);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("corrupt job payload for " + jobId, e);
        }
    }

    public void cache(JobContext ctx) {
        try { redis.opsForValue().set(RedisKeys.context(ctx.jobId()), mapper.writeValueAsString(ctx), RedisKeys.CONTEXT_TTL); }
        catch (Exception e) { /* optional optimisation */ }
    }
}
