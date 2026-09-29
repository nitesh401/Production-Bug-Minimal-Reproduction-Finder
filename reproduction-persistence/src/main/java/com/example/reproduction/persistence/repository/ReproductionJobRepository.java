package com.example.reproduction.persistence.repository;

import com.example.reproduction.domain.JobState;
import com.example.reproduction.persistence.entity.ReproductionJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReproductionJobRepository extends JpaRepository<ReproductionJobEntity, String> {
    Optional<ReproductionJobEntity> findByIdempotencyKey(String key);
    List<ReproductionJobEntity> findByStatusInAndDeadlineAtBefore(List<JobState> statuses, Instant now);
    List<ReproductionJobEntity> findByStatusIn(List<JobState> statuses);
}
