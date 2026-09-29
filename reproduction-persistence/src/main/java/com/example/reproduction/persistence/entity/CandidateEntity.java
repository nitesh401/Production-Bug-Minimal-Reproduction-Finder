package com.example.reproduction.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "candidate")
public class CandidateEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "job_id") public String jobId;
    @Column(name = "candidate_hash") public String candidateHash;
    @Column(name = "field_count") public int fieldCount;
    @Column(name = "canonical_text", columnDefinition = "MEDIUMTEXT") public String canonicalText;
    @Column(name = "created_at") public Instant createdAt;
    /** Relational membership: which input fields (by index) this candidate retains. */
    @ElementCollection @CollectionTable(name = "candidate_field", joinColumns = @JoinColumn(name = "candidate_id"))
    @Column(name = "field_idx") public Set<Integer> fieldIndexes = new HashSet<>();
    protected CandidateEntity() {}
    public CandidateEntity(String jobId, String hash, int fieldCount, Instant createdAt) {
        this.jobId = jobId; this.candidateHash = hash; this.fieldCount = fieldCount; this.createdAt = createdAt;
    }
}
