package com.example.reproduction.domain;

/** What the system under test returned for one attempt. */
public record ResponseObservation(int status, String errorCode, String body, long latencyMillis, String exception) {}
