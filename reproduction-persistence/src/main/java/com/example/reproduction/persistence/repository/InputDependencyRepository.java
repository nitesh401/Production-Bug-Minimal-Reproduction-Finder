package com.example.reproduction.persistence.repository;

import com.example.reproduction.persistence.entity.InputDependencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InputDependencyRepository extends JpaRepository<InputDependencyEntity, Long> {
    List<InputDependencyEntity> findByJobId(String jobId);
}
