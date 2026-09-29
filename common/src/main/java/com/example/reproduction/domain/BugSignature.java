package com.example.reproduction.domain;

/** Declarative description of "the bug". All non-null criteria must match (logical AND). */
public record BugSignature(Integer httpStatus, String errorCode, String bodyPattern,
                           Long minLatencyMillis, String exceptionSignature) {
    public static BugSignature status(int s) { return new BugSignature(s, null, null, null, null); }
    public static BugSignature statusAndCode(int s, String c) { return new BugSignature(s, c, null, null, null); }
}
