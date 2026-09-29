package com.example.reproduction.messaging;

import java.time.Instant;
import java.util.List;

/** Fed to the analysis service so that historical field importance can drive PRIORITY ordering later. */
public record AnalysisEvent(String jobId, String correlationId, Instant timestamp, List<String> originalFields,
                            List<List<String>> minimalCandidates) {}
