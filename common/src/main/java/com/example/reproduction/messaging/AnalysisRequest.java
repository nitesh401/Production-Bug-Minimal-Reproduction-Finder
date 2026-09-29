package com.example.reproduction.messaging;

import com.example.reproduction.domain.Dependency;

import java.util.List;
import java.util.Map;

/** Either give {@code input} (nested JSON, will be flattened) or an explicit list of {@code fields}. */
public record AnalysisRequest(Map<String, Object> input, List<String> fields, List<Dependency> explicitDependencies) {}
