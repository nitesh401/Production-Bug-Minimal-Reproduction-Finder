package com.example.reproduction.persistence.repository;

import com.example.reproduction.persistence.entity.JobAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobAttemptRepository extends JpaRepository<JobAttemptEntity, Long> {
    Optional<JobAttemptEntity> findFirstByJobIdOrderByAttemptNoDesc(String jobId);
}
