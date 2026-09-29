package com.example.reproduction.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "worker_task")
public class WorkerTaskEntity {
    public enum Status { PENDING, RUNNING, COMPLETED, FAILED, DEAD }
    @Id @Column(name = "task_id", length = 36) public String taskId;
    @Column(name = "job_id") public String jobId;
    public String type;
    @Enumerated(EnumType.STRING) public Status status;
    public int attempt;
    @Column(name = "banned_json", columnDefinition = "TEXT") public String bannedJson;
    @Column(name = "result_json", columnDefinition = "MEDIUMTEXT") public String resultJson;
    @Column(name = "worker_id") public String workerId;
    @Column(name = "lease_expires_at") public Instant leaseExpiresAt;
    @Column(name = "created_at") public Instant createdAt;
    @Column(name = "updated_at") public Instant updatedAt;
    @Version public long version;
    protected WorkerTaskEntity() {}
    public WorkerTaskEntity(String taskId) { this.taskId = taskId; }
}
