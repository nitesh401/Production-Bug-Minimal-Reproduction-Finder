package com.example.reproduction.domain;

/** One accepted reduction (an audit-trail entry). */
public record ReductionStep(int index, String phase, String action, int sizeBefore, int sizeAfter,
                            int granularity, String candidateHash, java.util.List<String> keptFields) {}
