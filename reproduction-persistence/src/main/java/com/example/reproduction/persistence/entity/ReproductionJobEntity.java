package com.example.reproduction.persistence.entity;

import com.example.reproduction.domain.JobState;
import jakarta.persistence.*;

import java.time.Instant;

/** Persistence-only holder (never exposed through REST; see the mappers in the services). */
@Entity
@Table(name = "reproduction_job")
public class ReproductionJobEntity {
    @Id @Column(length = 36) public String id;
    public String name;
    @Enumerated(EnumType.STRING) public JobState status;
    public String strategy;
    @Column(name = "scenario_id") public String scenarioId;
    @Column(name = "idempotency_key") public String idempotencyKey;
    @Column(name = "request_fingerprint") public String requestFingerprint;
    @Column(name = "initial_input_json", columnDefinition = "MEDIUMTEXT") public String initialInputJson;
    @Column(name = "options_json", columnDefinition = "TEXT") public String optionsJson;
    @Column(name = "original_field_count") public int originalFieldCount;
    @Column(name = "result_json", columnDefinition = "MEDIUMTEXT") public String resultJson;
    @Column(name = "error_message") public String errorMessage;
    @Column(name = "created_at") public Instant createdAt;
    @Column(name = "updated_at") public Instant updatedAt;
    @Column(name = "started_at") public Instant startedAt;
    @Column(name = "completed_at") public Instant completedAt;
    @Column(name = "deadline_at") public Instant deadlineAt;
    @Version public long version;

    protected ReproductionJobEntity() {}
    public ReproductionJobEntity(String id) { this.id = id; }
}
