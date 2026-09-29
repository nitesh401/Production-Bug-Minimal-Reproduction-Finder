package com.example.reproduction.persistence.repository;

import com.example.reproduction.persistence.entity.InputFieldEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InputFieldRepository extends JpaRepository<InputFieldEntity, InputFieldEntity.Key> {
    List<InputFieldEntity> findByJobIdOrderByIdx(String jobId);
    long countByJobId(String jobId);
}
