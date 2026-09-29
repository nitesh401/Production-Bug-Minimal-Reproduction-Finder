package com.example.reproduction.orchestrator.application;

import com.example.reproduction.domain.JobState;
import com.example.reproduction.persistence.entity.ReproductionJobEntity;
import com.example.reproduction.persistence.entity.WorkerTaskEntity;
import com.example.reproduction.persistence.repository.ReproductionJobRepository;
import com.example.reproduction.persistence.repository.WorkerTaskRepository;
import com.example.reproduction.orchestrator.config.OrchestratorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The orchestrator's safety net (also how it survives its own restart, because ALL state is in MySQL):
 * 1. task leases that expired (worker crash, lost publish, stale consumer)  -> re-enqueue with attempt+1, or give up (DLQ);
 * 2. jobs stuck in ANALYZING (crash between the two submit transactions)     -> back to PENDING and re-run;
 * 3. jobs past their deadline (maxExecutionTime)                             -> TIMED_OUT with the partial result.
 * A Redis lock keeps multiple orchestrator instances from sweeping simultaneously; optimistic locking on the
 * entities guarantees correctness even if the lock is unavailable.
 */
@Component
public class TimeoutSweeper {
    private static final Logger log = LoggerFactory.getLogger(TimeoutSweeper.class);
    private static final List<WorkerTaskEntity.Status> OUTSTANDING = List.of(WorkerTaskEntity.Status.PENDING, WorkerTaskEntity.Status.RUNNING);

    private final WorkerTaskRepository tasks;
    private final ReproductionJobRepository jobs;
    private final JobOrchestrator orchestrator;
    private final JobStateMachine machine;
    private final ProgressTracker progress;
    private final StringRedisTemplate redis;
    private final TransactionTemplate tx;
    private final OrchestratorProperties props;

    public TimeoutSweeper(WorkerTaskRepository tasks, ReproductionJobRepository jobs, JobOrchestrator orchestrator, JobStateMachine machine,
                          ProgressTracker progress, StringRedisTemplate redis, TransactionTemplate tx, OrchestratorProperties props) {
        this.tasks = tasks; this.jobs = jobs; this.orchestrator = orchestrator; this.machine = machine; this.progress = progress;
        this.redis = redis; this.tx = tx; this.props = props;
    }

    @Scheduled(fixedDelayString = "${orchestrator.sweep-interval-ms:5000}", initialDelayString = "${orchestrator.sweep-initial-delay-ms:10000}")
    public void sweep() {
        if (!acquireSweepLock()) return;
        try {
            sweepExpiredTasks();
            sweepStuckAnalyzing();
            sweepExpiredJobs();
        } catch (OptimisticLockingFailureException e) {
            log.debug("another orchestrator instance won the race: {}", e.getMessage());
        } catch (DataAccessException e) {
            log.error("MySQL unavailable during sweep, will retry next tick: {}", e.toString());
        }
    }

    void sweepExpiredTasks() {
        for (WorkerTaskEntity expired : tasks.findByStatusInAndLeaseExpiresAtBefore(OUTSTANDING, Instant.now())) {
            try {
                tx.executeWithoutResult(s -> {
                    WorkerTaskEntity t = tasks.findById(expired.taskId).orElseThrow();
                    if (!OUTSTANDING.contains(t.status)) return; // completed meanwhile
                    ReproductionJobEntity job = jobs.findById(t.jobId).orElseThrow();
                    if (job.status.terminal()) { t.status = WorkerTaskEntity.Status.DEAD; t.updatedAt = Instant.now(); tasks.save(t); return; }
                    orchestrator.retryOrGiveUp(t, job, "lease expired (worker crash, lost message or slow task)", null);
                    tasks.save(t);
                });
            } catch (OptimisticLockingFailureException e) { log.debug("task {} handled by someone else", expired.taskId); }
        }
    }

    void sweepStuckAnalyzing() {
        Instant cutoff = Instant.now().minus(props.analyzingStuckAfter());
        for (ReproductionJobEntity j : jobs.findByStatusIn(List.of(JobState.ANALYZING))) {
            if (j.updatedAt.isAfter(cutoff)) continue;
            Boolean reset = tx.execute(s -> {
                ReproductionJobEntity job = jobs.findById(j.id).orElseThrow();
                if (job.status != JobState.ANALYZING) return false;
                machine.transition(job, JobState.PENDING, Instant.now());
                jobs.save(job);
                return true;
            });
            if (Boolean.TRUE.equals(reset)) { log.warn("job {} was stuck in ANALYZING, restarting submission", j.id); orchestrator.handleSubmit(j.id, "recovery"); }
        }
    }

    void sweepExpiredJobs() {
        for (ReproductionJobEntity j : jobs.findByStatusInAndDeadlineAtBefore(List.of(JobState.SEARCHING), Instant.now())) {
            try {
                tx.executeWithoutResult(s -> {
                    ReproductionJobEntity job = jobs.findById(j.id).orElseThrow();
                    if (job.status != JobState.SEARCHING) return;
                    orchestrator.finish(job, JobState.TIMED_OUT, null);
                    for (WorkerTaskEntity t : tasks.findByJobIdAndStatusIn(job.id, OUTSTANDING)) { t.status = WorkerTaskEntity.Status.DEAD; t.updatedAt = Instant.now(); tasks.save(t); }
                    progress.setCancelled(job.id, true); // tell running workers to stop
                });
                log.warn("job {} timed out", j.id);
            } catch (OptimisticLockingFailureException e) { log.debug("job {} handled by someone else", j.id); }
        }
    }

    private boolean acquireSweepLock() {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("lock:orchestrator:sweeper", "1", Duration.ofSeconds(4)));
        } catch (DataAccessException e) {
            return true; // fail open: the sweep is idempotent and protected by optimistic locking
        }
    }
}
