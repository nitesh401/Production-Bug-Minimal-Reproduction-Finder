package com.example.reproduction.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "input_dependency")
public class InputDependencyEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "job_id") public String jobId;
    @Column(name = "from_idx") public int fromIdx;
    @Column(name = "to_idx") public int toIdx;
    public String reason;
    protected InputDependencyEntity() {}
    public InputDependencyEntity(String jobId, int fromIdx, int toIdx, String reason) {
        this.jobId = jobId; this.fromIdx = fromIdx; this.toIdx = toIdx; this.reason = reason;
    }
}
