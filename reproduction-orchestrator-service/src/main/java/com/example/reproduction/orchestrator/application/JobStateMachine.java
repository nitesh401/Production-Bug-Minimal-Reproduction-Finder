package com.example.reproduction.orchestrator.application;

import com.example.reproduction.domain.JobState;
import com.example.reproduction.persistence.entity.ReproductionJobEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Single choke point for job state changes (State pattern: legality lives in {@link JobState}). */
@Component
public class JobStateMachine {
    public void transition(ReproductionJobEntity job, JobState to, Instant now) {
        if (!job.status.canTransitionTo(to))
            throw new IllegalJobStateException("Illegal job transition " + job.status + " -> " + to + " for job " + job.id);
        job.status = to;
        job.updatedAt = now;
        if (to == JobState.SEARCHING && job.startedAt == null) job.startedAt = now;
        if (to.terminal()) job.completedAt = now; else job.completedAt = null;
    }
}
