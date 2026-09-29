package com.example.reproduction.persistence.repository;

import com.example.reproduction.persistence.entity.CandidateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CandidateRepository extends JpaRepository<CandidateEntity, Long> {
    Optional<CandidateEntity> findByJobIdAndCandidateHash(String jobId, String hash);
}
