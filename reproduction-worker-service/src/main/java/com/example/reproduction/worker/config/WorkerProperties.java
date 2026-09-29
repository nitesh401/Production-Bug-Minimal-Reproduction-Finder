package com.example.reproduction.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "worker")
public record WorkerProperties(String id, String simulatorUrl, String orchestratorUrl, Duration evaluationTimeout,
                               int l1CacheSize, Duration taskLease, int maxEvaluationThreads) {
    public WorkerProperties {
        if (id == null || id.isBlank()) id = "worker-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        if (simulatorUrl == null) simulatorUrl = "http://localhost:8084";
        if (orchestratorUrl == null) orchestratorUrl = "http://localhost:8081";
        if (evaluationTimeout == null) evaluationTimeout = Duration.ofSeconds(5);
        if (l1CacheSize <= 0) l1CacheSize = 50_000;
        if (taskLease == null) taskLease = Duration.ofMinutes(10);
        if (maxEvaluationThreads <= 0) maxEvaluationThreads = 8;
    }
}
