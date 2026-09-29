package com.example.reproduction.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "bug_signature")
public class BugSignatureEntity {
    @Id @Column(name = "job_id", length = 36) public String jobId;
    @Column(name = "http_status") public Integer httpStatus;
    @Column(name = "error_code") public String errorCode;
    @Column(name = "body_pattern") public String bodyPattern;
    @Column(name = "min_latency_ms") public Long minLatencyMs;
    @Column(name = "exception_signature") public String exceptionSignature;
    protected BugSignatureEntity() {}
    public BugSignatureEntity(String jobId) { this.jobId = jobId; }
}
