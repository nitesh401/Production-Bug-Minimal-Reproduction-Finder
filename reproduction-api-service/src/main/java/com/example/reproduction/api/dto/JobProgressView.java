package com.example.reproduction.api.dto;

import java.util.Map;

public record JobProgressView(String jobId, String state, Map<String, String> details) {}
