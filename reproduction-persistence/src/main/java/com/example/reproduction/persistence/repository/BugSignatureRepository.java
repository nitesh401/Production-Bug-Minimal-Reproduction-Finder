package com.example.reproduction.persistence.repository;

import com.example.reproduction.persistence.entity.BugSignatureEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BugSignatureRepository extends JpaRepository<BugSignatureEntity, String> {}
