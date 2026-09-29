package com.example.reproduction.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "evaluation_result")
public class EvaluationResultEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "job_id") public String jobId;
    @Column(name = "candidate_id") public Long candidateId;
    @Column(name = "task_id") public String taskId;
    public int attempt;
    public String status;
    @Column(name = "attempts_run") public int attemptsRun;
    public int reproductions;
    @Column(name = "reproduction_rate") public double reproductionRate;
    @Column(name = "duration_ms") public long durationMs;
    public String source;
    @Column(name = "created_at") public Instant createdAt;
    protected EvaluationResultEntity() {}
    public EvaluationResultEntity(String jobId, Long candidateId, String taskId, int attempt) {
        this.jobId = jobId; this.candidateId = candidateId; this.taskId = taskId; this.attempt = attempt;
    }
}
