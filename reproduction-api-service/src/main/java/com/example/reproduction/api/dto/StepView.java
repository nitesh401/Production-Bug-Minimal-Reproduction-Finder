package com.example.reproduction.api.dto;

public record StepView(int index, String taskId, String phase, String action, int sizeBefore, int sizeAfter, int granularity, String candidateHash) {}
