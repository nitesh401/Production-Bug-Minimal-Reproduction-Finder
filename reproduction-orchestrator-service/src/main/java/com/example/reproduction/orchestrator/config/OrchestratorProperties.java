package com.example.reproduction.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "orchestrator")
public record OrchestratorProperties(String analysisUrl, Duration taskTimeout, int maxTaskAttempts, int maxTasksPerJob,
                                     Duration analyzingStuckAfter) {
    public OrchestratorProperties {
        if (analysisUrl == null) analysisUrl = "http://localhost:8083";
        if (taskTimeout == null) taskTimeout = Duration.ofMinutes(5);
        if (maxTaskAttempts <= 0) maxTaskAttempts = 3;
        if (maxTasksPerJob <= 0) maxTasksPerJob = 50;
        if (analyzingStuckAfter == null) analyzingStuckAfter = Duration.ofSeconds(60);
    }
}
