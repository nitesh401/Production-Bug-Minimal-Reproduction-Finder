package com.example.reproduction.persistence.repository;

import com.example.reproduction.persistence.entity.ReductionStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReductionStepRepository extends JpaRepository<ReductionStepEntity, Long> {
    List<ReductionStepEntity> findByJobIdOrderByIdAsc(String jobId);
    boolean existsByJobIdAndTaskIdAndStepIndex(String jobId, String taskId, int stepIndex);
}
