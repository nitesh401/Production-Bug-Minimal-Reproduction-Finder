package com.example.reproduction.messaging;

import java.time.Instant;

public record JobCommand(Type type, String jobId, String correlationId, Instant timestamp) {
    public enum Type { SUBMIT, CANCEL, RESUME }
}
