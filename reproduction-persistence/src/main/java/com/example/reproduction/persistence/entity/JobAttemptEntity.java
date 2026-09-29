package com.example.reproduction.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "job_attempt")
public class JobAttemptEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "job_id") public String jobId;
    @Column(name = "attempt_no") public int attemptNo;
    @Column(name = "trigger_type") public String triggerType;
    @Column(name = "started_at") public Instant startedAt;
    @Column(name = "ended_at") public Instant endedAt;
    public String outcome;
    protected JobAttemptEntity() {}
    public JobAttemptEntity(String jobId, int attemptNo, String triggerType, Instant startedAt) {
        this.jobId = jobId; this.attemptNo = attemptNo; this.triggerType = triggerType; this.startedAt = startedAt;
    }
}
