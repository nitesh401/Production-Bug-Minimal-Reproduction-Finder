package com.example.reproduction.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "reduction_step")
public class ReductionStepEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "job_id") public String jobId;
    @Column(name = "task_id") public String taskId;
    @Column(name = "step_index") public int stepIndex;
    public String phase;
    public String action;
    @Column(name = "size_before") public int sizeBefore;
    @Column(name = "size_after") public int sizeAfter;
    public int granularity;
    @Column(name = "candidate_hash") public String candidateHash;
    @Column(name = "created_at") public Instant createdAt;
    protected ReductionStepEntity() {}
    public ReductionStepEntity(String jobId, String taskId, int stepIndex) { this.jobId = jobId; this.taskId = taskId; this.stepIndex = stepIndex; }
}
