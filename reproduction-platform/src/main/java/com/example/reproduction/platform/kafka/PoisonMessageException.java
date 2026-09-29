package com.example.reproduction.platform.kafka;

import com.example.reproduction.exception.ReproductionException;

/** The message can never be processed (missing fields, unknown job...). Never retried; goes to the DLQ. */
public class PoisonMessageException extends ReproductionException {
    public PoisonMessageException(String message) { super(message); }
}
