package com.example.reproduction.api.application;

import com.example.reproduction.messaging.JobCommand;
import com.example.reproduction.messaging.Topics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class JobCommandPublisher {
    private final KafkaTemplate<String, Object> kafka;
    public JobCommandPublisher(KafkaTemplate<String, Object> kafka) { this.kafka = kafka; }

    public void send(JobCommand.Type type, String jobId, String correlationId) {
        try {
            kafka.send(Topics.JOBS, jobId, new JobCommand(type, jobId, correlationId, Instant.now())).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        catch (ExecutionException | TimeoutException e) { throw new IllegalStateException("could not publish " + type + " for job " + jobId, e); }
    }
}
