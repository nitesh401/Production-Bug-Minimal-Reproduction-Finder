package com.example.reproduction.api.dto;

import com.example.reproduction.domain.JobState;

import java.time.Instant;

public record JobView(String jobId, String name, JobState status, String strategy, int originalFieldCount,
                      Instant createdAt, Instant updatedAt, Instant startedAt, Instant completedAt, String errorMessage) {}
