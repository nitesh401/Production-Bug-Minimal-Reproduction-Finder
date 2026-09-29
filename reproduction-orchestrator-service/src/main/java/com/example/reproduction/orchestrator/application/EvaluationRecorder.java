package com.example.reproduction.orchestrator.application;

import com.example.reproduction.messaging.EvaluationEvent;
import com.example.reproduction.persistence.entity.CandidateEntity;
import com.example.reproduction.persistence.entity.EvaluationResultEntity;
import com.example.reproduction.persistence.entity.InputFieldEntity;
import com.example.reproduction.persistence.repository.CandidateRepository;
import com.example.reproduction.persistence.repository.EvaluationResultRepository;
import com.example.reproduction.persistence.repository.InputFieldRepository;
import com.example.reproduction.persistence.repository.ReproductionJobRepository;
import com.example.reproduction.platform.kafka.PoisonMessageException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists the audit trail of evaluations. This is OFF the algorithm's hot path: workers never query MySQL per
 * candidate; they publish events and this consumer stores them idempotently (unique keys make redelivery harmless).
 */
@Service
public class EvaluationRecorder {
    private final CandidateRepository candidates;
    private final EvaluationResultRepository evaluations;
    private final InputFieldRepository fields;
    private final ReproductionJobRepository jobs;
    private final ProgressTracker progress;
    private final Map<String, Map<String, Integer>> fieldIndexCache = new ConcurrentHashMap<>();

    public EvaluationRecorder(CandidateRepository candidates, EvaluationResultRepository evaluations, InputFieldRepository fields,
                              ReproductionJobRepository jobs, ProgressTracker progress) {
        this.candidates = candidates; this.evaluations = evaluations; this.fields = fields; this.jobs = jobs; this.progress = progress;
    }

    @Transactional
    public boolean record(EvaluationEvent e) {
        if (!jobs.existsById(e.jobId())) throw new PoisonMessageException("evaluation for unknown job " + e.jobId());
        Map<String, Integer> idx = fieldIndex(e.jobId());
        CandidateEntity c = candidates.findByJobIdAndCandidateHash(e.jobId(), e.candidateHash()).orElseGet(() -> {
            CandidateEntity n = new CandidateEntity(e.jobId(), e.candidateHash(), e.fieldPaths().size(), Instant.now());
            for (String p : e.fieldPaths()) { Integer i = idx.get(p); if (i != null) n.fieldIndexes.add(i); }
            return candidates.save(n);
        });
        if (evaluations.existsByJobIdAndCandidateIdAndTaskIdAndAttempt(e.jobId(), c.id, e.taskId(), e.attempt())) return false; // duplicate delivery
        EvaluationResultEntity r = new EvaluationResultEntity(e.jobId(), c.id, e.taskId(), e.attempt());
        r.status = e.status().name();
        r.attemptsRun = e.attemptsRun();
        r.reproductions = e.reproductions();
        r.reproductionRate = e.reproductionRate();
        r.durationMs = e.durationMillis();
        r.source = e.source();
        r.createdAt = Instant.now();
        evaluations.save(r);
        progress.increment(e.jobId(), "evaluationsRecorded", 1);
        if ("CACHE".equals(e.source())) progress.increment(e.jobId(), "cacheHits", 1);
        return true;
    }

    private Map<String, Integer> fieldIndex(String jobId) {
        return fieldIndexCache.computeIfAbsent(jobId, id -> {
            Map<String, Integer> m = new HashMap<>();
            for (InputFieldEntity f : fields.findByJobIdOrderByIdx(id)) m.put(f.path, f.idx);
            if (fieldIndexCache.size() > 200) fieldIndexCache.clear(); // crude bound; entries are cheap to rebuild
            return m;
        });
    }
}
