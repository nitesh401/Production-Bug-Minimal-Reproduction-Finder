package com.example.reproduction.api;

import com.example.reproduction.api.application.InvalidJobRequestException;
import com.example.reproduction.api.application.JobCommandPublisher;
import com.example.reproduction.api.application.JobNotFoundException;
import com.example.reproduction.api.application.JobService;
import com.example.reproduction.api.dto.CreateJobRequest;
import com.example.reproduction.api.dto.JobView;
import com.example.reproduction.domain.JobState;
import com.example.reproduction.persistence.entity.ReproductionJobEntity;
import com.example.reproduction.persistence.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Idempotency and validation logic in isolation (repositories mocked: no DB needed for these rules). */
class JobServiceTest {
    ReproductionJobRepository jobs = mock(ReproductionJobRepository.class);
    BugSignatureRepository signatures = mock(BugSignatureRepository.class);
    JobCommandPublisher publisher = mock(JobCommandPublisher.class);
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    JobService service;

    static CreateJobRequest req(String name) {
        return new CreateJobRequest(name, Map.of("amount", 15000, "currency", "INR"),
                new CreateJobRequest.BugSignatureDto(500, "PAYMENT_ROUTE_FAILURE", null, null, null),
                "SIMPLE_AND", 3, 0.66, 1000, 60L, 4, 5, 0, true, com.example.reproduction.domain.StrategyType.HYBRID);
    }

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(any())).thenReturn(Map.of());
        when(jobs.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new JobService(jobs, signatures, mock(ReductionStepRepository.class), mock(EvaluationResultRepository.class),
                mock(CandidateRepository.class), publisher, redis, new ObjectMapper(), new SimpleMeterRegistry());
    }

    @Test
    void createsAPendingJobAndPublishesSubmit() {
        when(jobs.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        JobView v = service.create(req("job-1"), null, "corr-1");
        assertEquals(JobState.PENDING, v.status());
        verify(publisher).send(eq(com.example.reproduction.messaging.JobCommand.Type.SUBMIT), any(), eq("corr-1"));
    }

    @Test
    void sameIdempotencyKeyReturnsOriginalJobWithoutPublishingAgain() {
        ReproductionJobEntity existing = new ReproductionJobEntity("job-orig");
        existing.name = "job-1"; existing.status = JobState.SEARCHING;
        existing.requestFingerprint = fingerprintOf(req("job-1"));
        when(jobs.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));
        JobView v = service.create(req("job-1"), "key-1", "corr-2");
        assertEquals("job-orig", v.jobId());
        verifyNoInteractions(publisher);
    }

    @Test
    void sameKeyDifferentBodyIsRejected() {
        ReproductionJobEntity existing = new ReproductionJobEntity("job-orig");
        existing.requestFingerprint = "different-fingerprint";
        when(jobs.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));
        assertThrows(InvalidJobRequestException.class, () -> service.create(req("job-1"), "key-1", "corr"));
    }

    @Test
    void unknownJobThrowsNotFound() {
        when(jobs.findById("missing")).thenReturn(Optional.empty());
        assertThrows(JobNotFoundException.class, () -> service.get("missing"));
    }

    @Test
    void resumeRejectedForNonTerminalOrCompletedJobs() {
        ReproductionJobEntity j = new ReproductionJobEntity("j"); j.status = JobState.SEARCHING;
        when(jobs.findById("j")).thenReturn(Optional.of(j));
        assertThrows(InvalidJobRequestException.class, () -> service.resume("j", "corr"));
        j.status = JobState.COMPLETED;
        assertThrows(InvalidJobRequestException.class, () -> service.resume("j", "corr"));
        j.status = JobState.FAILED;
        service.resume("j", "corr");
        verify(publisher).send(com.example.reproduction.messaging.JobCommand.Type.RESUME, "j", "corr");
    }

    @Test
    void resultNotReadyYieldsConflict() {
        ReproductionJobEntity j = new ReproductionJobEntity("j"); j.status = JobState.SEARCHING; j.resultJson = null;
        when(jobs.findById("j")).thenReturn(Optional.of(j));
        assertThrows(InvalidJobRequestException.class, () -> service.result("j"));
    }

    private String fingerprintOf(CreateJobRequest r) {
        // recompute the same way JobService does, to assert the "identical body" path works in isolation
        try {
            var m = new java.util.TreeMap<String, Object>();
            m.put("name", r.name()); m.put("initialInput", r.initialInput());
            m.put("bugSignature", new ObjectMapper().writeValueAsString(r.bugSignature()));
            var opts = new com.example.reproduction.domain.JobOptions(r.strategy(), r.evaluationAttempts(), r.minimumReproductionRate(),
                    r.maxEvaluations(), r.maxExecutionSeconds(), r.maxConcurrentEvaluations(), r.maxSolutions(), r.randomRestarts(), r.assumeMonotonic());
            m.put("options", new ObjectMapper().writeValueAsString(opts)); m.put("scenarioId", r.scenarioId());
            return com.example.reproduction.util.Hashing.sha256Hex(com.example.reproduction.util.CanonicalJson.write(m));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
