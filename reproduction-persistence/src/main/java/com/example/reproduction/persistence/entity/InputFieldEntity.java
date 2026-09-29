package com.example.reproduction.persistence.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "input_field")
@IdClass(InputFieldEntity.Key.class)
public class InputFieldEntity {
    public record Key(String jobId, int idx) implements Serializable {}
    @Id @Column(name = "job_id", length = 36) public String jobId;
    @Id public int idx;
    public String path;
    @Column(name = "value_json", columnDefinition = "TEXT") public String valueJson;
    @Column(name = "payload_size") public int payloadSize;
    protected InputFieldEntity() {}
    public InputFieldEntity(String jobId, int idx, String path, String valueJson, int payloadSize) {
        this.jobId = jobId; this.idx = idx; this.path = path; this.valueJson = valueJson; this.payloadSize = payloadSize;
    }
    @Override public boolean equals(Object o) { return o instanceof InputFieldEntity f && f.idx == idx && Objects.equals(f.jobId, jobId); }
    @Override public int hashCode() { return Objects.hash(jobId, idx); }
}
