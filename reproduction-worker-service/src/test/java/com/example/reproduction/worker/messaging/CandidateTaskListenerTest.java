package com.example.reproduction.worker.messaging;

import com.example.reproduction.domain.StopReason;
import com.example.reproduction.messaging.CandidateTask;
import com.example.reproduction.messaging.TaskResult;
import com.example.reproduction.platform.kafka.PoisonMessageException;
import com.example.reproduction.worker.config.WorkerProperties;
import com.example.reproduction.worker.execution.SearchTaskExecutor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CandidateTaskListenerTest {
    SearchTaskExecutor executor = mock(SearchTaskExecutor.class);
    TaskDeduplicator dedup = mock(TaskDeduplicator.class);
    KafkaTemplate<String, Object> kafka;
    CandidateTaskListener listener;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        listener = new CandidateTaskListener(executor, dedup, kafka, new SimpleMeterRegistry(),
                new WorkerProperties("w1", null, null, null, 0, Duration.ofMinutes(1), 0));
    }

    static CandidateTask task(int attempt) {
        return new CandidateTask("j", "t", "c", "h", attempt, Instant.now(), "corr", CandidateTask.Type.PRIMARY, List.of(), null);
    }

    static TaskResult result(int attempt) {
        return new TaskResult("j", "t", "c", "h", attempt, Instant.now(), "corr", TaskResult.Outcome.SUCCESS, List.of("a"), true, 1.0,
                List.of(), List.of(), StopReason.COMPLETED, "", 1, 0);
    }

    @Test
    void duplicateOfFinishedTaskRepublishesStoredResultWithoutExecuting() {
        when(dedup.finished("t")).thenReturn(Optional.of(result(1)));
        listener.onTask(task(1));
        verify(executor, never()).execute(any());
        verify(kafka).send(eq("reproduction.results"), eq("j"), any());
    }

    @Test
    void taskOwnedByAnotherWorkerIsSkipped() {
        when(dedup.finished("t")).thenReturn(Optional.empty());
        when(dedup.tryAcquire(eq("t"), eq(1), anyString(), any())).thenReturn(false);
        listener.onTask(task(1));
        verify(executor, never()).execute(any());
        verify(kafka, never()).send(anyString(), anyString(), any());
    }

    @Test
    void freshTaskExecutesRemembersAndPublishes() {
        when(dedup.finished("t")).thenReturn(Optional.empty());
        when(dedup.tryAcquire(eq("t"), eq(1), anyString(), any())).thenReturn(true);
        when(executor.execute(any())).thenReturn(result(1));
        listener.onTask(task(1));
        verify(dedup).remember(any());
        verify(kafka).send(eq("reproduction.results"), eq("j"), any());
    }

    @Test
    void retryAttemptRunsEvenIfOlderAttemptFinishedNever() {
        when(dedup.finished("t")).thenReturn(Optional.empty());
        when(dedup.tryAcquire(eq("t"), eq(2), anyString(), any())).thenReturn(true);
        when(executor.execute(any())).thenReturn(result(2));
        listener.onTask(task(2));
        verify(executor).execute(any());
    }

    @Test
    void failureReleasesLockAndPropagatesForRetryHandling() {
        when(dedup.finished("t")).thenReturn(Optional.empty());
        when(dedup.tryAcquire(eq("t"), eq(1), anyString(), any())).thenReturn(true);
        when(executor.execute(any())).thenThrow(new IllegalStateException("boom"));
        assertThrows(IllegalStateException.class, () -> listener.onTask(task(1)));
        verify(dedup).release("t", 1);
    }

    @Test
    void poisonMessageIsRejectedWithoutTouchingAnything() {
        assertThrows(PoisonMessageException.class, () -> listener.onTask(new CandidateTask(null, "t", "c", "h", 1, Instant.now(), null, null, null, null)));
        assertThrows(PoisonMessageException.class, () -> listener.onTask(null));
        verifyNoInteractions(executor);
    }
}
