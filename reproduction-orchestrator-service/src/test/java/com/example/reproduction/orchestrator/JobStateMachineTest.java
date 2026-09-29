package com.example.reproduction.orchestrator;

import com.example.reproduction.domain.JobState;
import com.example.reproduction.orchestrator.application.IllegalJobStateException;
import com.example.reproduction.orchestrator.application.JobStateMachine;
import com.example.reproduction.persistence.entity.ReproductionJobEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class JobStateMachineTest {
    final JobStateMachine m = new JobStateMachine();

    static ReproductionJobEntity job(JobState s) { ReproductionJobEntity j = new ReproductionJobEntity("j"); j.status = s; return j; }

    @Test
    void happyPath() {
        ReproductionJobEntity j = job(JobState.PENDING);
        m.transition(j, JobState.ANALYZING, Instant.now());
        m.transition(j, JobState.SEARCHING, Instant.now());
        assertNotNull(j.startedAt);
        m.transition(j, JobState.COMPLETED, Instant.now());
        assertNotNull(j.completedAt);
    }

    @Test
    void completedJobsCannotBeResumedOrChanged() {
        assertThrows(IllegalJobStateException.class, () -> m.transition(job(JobState.COMPLETED), JobState.SEARCHING, Instant.now()));
        assertThrows(IllegalJobStateException.class, () -> m.transition(job(JobState.PENDING), JobState.COMPLETED, Instant.now()));
    }

    @Test
    void failedCancelledAndTimedOutJobsCanResume() {
        for (JobState s : new JobState[]{JobState.FAILED, JobState.CANCELLED, JobState.TIMED_OUT, JobState.PARTIAL}) {
            ReproductionJobEntity j = job(s);
            m.transition(j, JobState.SEARCHING, Instant.now());
            assertNull(j.completedAt);
        }
    }
}
