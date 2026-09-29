package com.example.reproduction.api.application;

import com.example.reproduction.api.dto.*;
import com.example.reproduction.domain.*;
import com.example.reproduction.messaging.JobCommand;
import com.example.reproduction.persistence.entity.*;
import com.example.reproduction.persistence.repository.*;
import com.example.reproduction.util.CanonicalJson;
import com.example.reproduction.util.Hashing;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * REST-facing application service: validation, idempotency and translating entities to view DTOs.
 * NO search algorithm here (by design, see the assignment) — this service only creates jobs and hands them to
 * the orchestrator via Kafka.
 */
@Service
public class JobService {
    private final ReproductionJobRepository jobs;
    private final BugSignatureRepository signatures;
    private final ReductionStepRepository steps;
    private final EvaluationResultRepository evaluations;
    private final CandidateRepository candidates;
    private final JobCommandPublisher publisher;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;

    public JobService(ReproductionJobRepository jobs, BugSignatureRepository signatures, ReductionStepRepository steps,
                      EvaluationResultRepository evaluations, CandidateRepository candidates, JobCommandPublisher publisher,
                      StringRedisTemplate redis, ObjectMapper mapper, MeterRegistry meters) {
        this.jobs = jobs; this.signatures = signatures; this.steps = steps; this.evaluations = evaluations;
        this.candidates = candidates; this.publisher = publisher; this.redis = redis; this.mapper = mapper; this.meters = meters;
    }

    /**
     * API-level idempotency: an Idempotency-Key that has already been used returns the ORIGINAL job untouched,
     * even if the body differs (the body is fingerprinted and compared; a mismatch is a client error, not a
     * silent divergence). No key => always a new job (fire-and-forget semantics).
     */
    @Transactional
    public JobView create(CreateJobRequest req, String idempotencyKey, String correlationId) {
        String fingerprint = fingerprint(req);
        if (idempotencyKey != null) {
            Optional<ReproductionJobEntity> existing = jobs.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                ReproductionJobEntity e = existing.get();
                if (!e.requestFingerprint.equals(fingerprint))
                    throw new InvalidJobRequestException("Idempotency-Key " + idempotencyKey + " was already used with a different request body");
                meters.counter("jobs.idempotent.replay").increment();
                return view(e);
            }
        }
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        ReproductionJobEntity job = new ReproductionJobEntity(id);
        job.name = req.name();
        job.status = JobState.PENDING;
        job.strategy = (req.strategy() == null ? StrategyType.HYBRID : req.strategy()).name();
        job.scenarioId = req.scenarioId();
        job.idempotencyKey = idempotencyKey;
        job.requestFingerprint = fingerprint;
        job.initialInputJson = toJson(req.initialInput());
        job.optionsJson = toJson(toOptions(req));
        job.createdAt = now; job.updatedAt = now;
        try {
            jobs.save(job);
        } catch (DataIntegrityViolationException e) {
            // race: two requests with the same key committed concurrently; the loser just returns the winner's job
            return jobs.findByIdempotencyKey(idempotencyKey).map(this::view)
                    .orElseThrow(() -> new IllegalStateException("idempotency conflict but no job found for key " + idempotencyKey, e));
        }
        BugSignatureEntity sig = new BugSignatureEntity(id);
        var b = req.bugSignature();
        sig.httpStatus = b.httpStatus(); sig.errorCode = b.errorCode(); sig.bodyPattern = b.bodyPattern();
        sig.minLatencyMs = b.minLatencyMillis(); sig.exceptionSignature = b.exceptionSignature();
        signatures.save(sig);
        publisher.send(JobCommand.Type.SUBMIT, id, correlationId);
        meters.counter("reproduction.jobs.submitted").increment();
        return view(job);
    }

    @Transactional(readOnly = true)
    public JobView get(String jobId) { return view(find(jobId)); }

    @Transactional(readOnly = true)
    public JobProgressView progress(String jobId) {
        find(jobId); // 404 if unknown
        Map<Object, Object> raw = redis.opsForHash().entries("job:" + jobId + ":progress");
        Map<String, String> details = new LinkedHashMap<>();
        raw.forEach((k, v) -> details.put(String.valueOf(k), String.valueOf(v)));
        return new JobProgressView(jobId, details.getOrDefault("state", find(jobId).status.name()), details);
    }

    @Transactional(readOnly = true)
    public JobResult result(String jobId) {
        ReproductionJobEntity job = find(jobId);
        if (job.resultJson == null) throw new InvalidJobRequestException("job " + jobId + " has not produced a result yet (status=" + job.status + ")");
        return read(job.resultJson, JobResult.class);
    }

    @Transactional(readOnly = true)
    public List<StepView> steps(String jobId) {
        find(jobId);
        int[] i = {0};
        return steps.findByJobIdOrderByIdAsc(jobId).stream()
                .map(s -> new StepView(++i[0], s.taskId, s.phase, s.action, s.sizeBefore, s.sizeAfter, s.granularity, s.candidateHash)).toList();
    }

    @Transactional(readOnly = true)
    public List<EvaluationView> evaluations(String jobId, int page, int size) {
        find(jobId);
        Map<Long, String> hashById = new HashMap<>();
        return evaluations.findByJobId(jobId, PageRequest.of(page, size)).stream()
                .map(e -> new EvaluationView(hashById.computeIfAbsent(e.candidateId, id -> candidates.findById(id).map(c -> c.candidateHash).orElse("?")),
                        e.taskId, e.attempt, e.status, e.attemptsRun, e.reproductions, e.reproductionRate, e.durationMs, e.source))
                .toList();
    }

    @Transactional(readOnly = true)
    public void cancel(String jobId, String correlationId) { find(jobId); publisher.send(JobCommand.Type.CANCEL, jobId, correlationId); }

    @Transactional(readOnly = true)
    public void resume(String jobId, String correlationId) {
        ReproductionJobEntity job = find(jobId);
        if (!job.status.terminal() || job.status == JobState.COMPLETED)
            throw new InvalidJobRequestException("job " + jobId + " is " + job.status + "; only FAILED, CANCELLED, TIMED_OUT or PARTIAL jobs can be resumed");
        publisher.send(JobCommand.Type.RESUME, jobId, correlationId);
    }

    private ReproductionJobEntity find(String jobId) { return jobs.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId)); }

    private JobView view(ReproductionJobEntity j) {
        return new JobView(j.id, j.name, j.status, j.strategy, j.originalFieldCount, j.createdAt, j.updatedAt, j.startedAt, j.completedAt, j.errorMessage);
    }

    private static JobOptions toOptions(CreateJobRequest r) {
        return new JobOptions(r.strategy(), r.evaluationAttempts(), r.minimumReproductionRate(), r.maxEvaluations(),
                r.maxExecutionSeconds(), r.maxConcurrentEvaluations(), r.maxSolutions(), r.randomRestarts(), r.assumeMonotonic());
    }

    /** Deterministic fingerprint of the semantically-relevant request fields (canonical JSON, sorted keys). */
    private String fingerprint(CreateJobRequest r) {
        Map<String, Object> m = new TreeMap<>();
        m.put("name", r.name()); m.put("initialInput", r.initialInput()); m.put("bugSignature", toJson(r.bugSignature()));
        m.put("options", toJson(toOptions(r))); m.put("scenarioId", r.scenarioId());
        return Hashing.sha256Hex(CanonicalJson.write(m));
    }

    private String toJson(Object o) { try { return mapper.writeValueAsString(o); } catch (Exception e) { throw new IllegalStateException(e); } }
    private <T> T read(String s, Class<T> c) { try { return mapper.readValue(s, c); } catch (Exception e) { throw new IllegalStateException("corrupt result JSON", e); } }
}
