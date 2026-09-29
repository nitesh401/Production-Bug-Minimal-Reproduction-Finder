package com.example.reproduction.persistence.repository;

import com.example.reproduction.persistence.entity.EvaluationResultEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationResultRepository extends JpaRepository<EvaluationResultEntity, Long> {
    Page<EvaluationResultEntity> findByJobId(String jobId, Pageable pageable);
    boolean existsByJobIdAndCandidateIdAndTaskIdAndAttempt(String jobId, Long candidateId, String taskId, int attempt);
    long countByJobId(String jobId);
    long countByJobIdAndStatus(String jobId, String status);
}
