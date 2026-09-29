package com.example.reproduction.messaging;

import com.example.reproduction.domain.BugSignature;
import com.example.reproduction.domain.Dependency;
import com.example.reproduction.domain.SearchConfig;

import java.util.List;
import java.util.Map;

/** Everything a worker needs to run tasks of a job. Served by the orchestrator, cached in Redis. */
public record JobContext(String jobId, Map<String, Object> initialInput, BugSignature bugSignature, SearchConfig config,
                         String scenarioId, List<Dependency> dependencies, Map<String, Double> keepHints) {}
