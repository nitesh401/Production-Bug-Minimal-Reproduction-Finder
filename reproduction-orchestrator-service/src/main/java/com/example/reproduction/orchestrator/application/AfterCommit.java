package com.example.reproduction.orchestrator.application;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Kafka sends must only happen after the database transaction that justifies them has committed. */
public final class AfterCommit {
    private AfterCommit() {}

    public static void run(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { r.run(); }
            });
        } else r.run();
    }
}
