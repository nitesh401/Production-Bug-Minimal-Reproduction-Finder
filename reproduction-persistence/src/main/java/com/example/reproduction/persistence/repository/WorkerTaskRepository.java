package com.example.reproduction.persistence.repository;

import com.example.reproduction.persistence.entity.WorkerTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface WorkerTaskRepository extends JpaRepository<WorkerTaskEntity, String> {
    List<WorkerTaskEntity> findByJobId(String jobId);
    List<WorkerTaskEntity> findByJobIdAndStatusIn(String jobId, List<WorkerTaskEntity.Status> statuses);
    List<WorkerTaskEntity> findByStatusInAndLeaseExpiresAtBefore(List<WorkerTaskEntity.Status> statuses, Instant now);
    long countByJobIdAndStatusIn(String jobId, List<WorkerTaskEntity.Status> statuses);
}
