package com.example.reproduction.worker.execution;

import com.example.reproduction.domain.*;
import com.example.reproduction.messaging.CandidateTask;
import com.example.reproduction.messaging.EvaluationEvent;
import com.example.reproduction.messaging.JobContext;
import com.example.reproduction.messaging.TaskResult;
import com.example.reproduction.simulator.SamplePayloads;
import com.example.reproduction.simulator.ScenarioEngine;
import com.example.reproduction.simulator.Scenarios;
import com.example.reproduction.worker.config.WorkerProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/** Worker pipeline without Docker: real engine + real HTTP oracle against a MockWebServer that runs the scenario engine. */
class SearchTaskExecutorTest {
    final ObjectMapper mapper = new ObjectMapper();
    MockWebServer sim;
    KafkaTemplate<String, Object> kafka;
    SearchTaskExecutor executor;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws IOException {
        sim = new MockWebServer();
        sim.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest r) {
                try {
                    Map<String, Object> body = mapper.readValue(r.getBody().readUtf8(), new TypeReference<>() {});
                    var o = ScenarioEngine.run(Scenarios.SIMPLE_AND, body, r.getHeader("X-Candidate-Hash"), Integer.parseInt(r.getHeader("X-Attempt")));
                    return new MockResponse().setResponseCode(o.status()).setHeader("Content-Type", "application/json").setBody(o.body());
                } catch (IOException e) { return new MockResponse().setResponseCode(400); }
            }
        });
        sim.start();

        JobContextClient contexts = Mockito.mock(JobContextClient.class);
        SearchConfig cfg = SearchConfig.builder().strategy(StrategyType.HYBRID).evaluationAttempts(1).maxConcurrentEvaluations(3).build();
        Mockito.when(contexts.load("job-1")).thenReturn(new JobContext("job-1", SamplePayloads.payment(),
                BugSignature.statusAndCode(500, "PAYMENT_ROUTE_FAILURE"), cfg, "SIMPLE_AND", List.of(), Map.of()));

        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = Mockito.mock(ValueOperations.class);
        Mockito.when(redis.opsForValue()).thenReturn(ops);
        kafka = Mockito.mock(KafkaTemplate.class);
        Mockito.when(kafka.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

        WorkerProperties props = new WorkerProperties("w1", sim.url("/").toString(), "http://unused", Duration.ofSeconds(2), 1000, Duration.ofMinutes(1), 4);
        executor = new SearchTaskExecutor(contexts, redis, kafka, new SimpleMeterRegistry(), WebClient.create(sim.url("/").toString()), mapper, props);
    }

    @AfterEach
    void tearDown() throws IOException { sim.shutdown(); }

    static CandidateTask task(List<String> banned) {
        return new CandidateTask("job-1", "task-aaaaaaaa", "cand-1", "hash", 1, Instant.now(), "corr-1", CandidateTask.Type.PRIMARY, banned, Instant.now().plusSeconds(60));
    }

    @Test
    void primaryTaskDiscoversTheMinimalReproductionThroughHttp() {
        TaskResult r = executor.execute(task(List.of()));
        assertEquals(TaskResult.Outcome.SUCCESS, r.outcome());
        assertEquals(List.of("amount", "currency", "customerType", "featureFlags.FAST_PATH"), r.minimalFields());
        assertTrue(r.provenMinimal());
        assertEquals(1.0, r.reproductionRate(), 1e-9);
        assertFalse(r.steps().isEmpty());

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(kafka, Mockito.atLeast(10)).send(Mockito.eq("reproduction.evaluations"), anyString(), events.capture());
        assertTrue(events.getAllValues().stream().allMatch(e -> e instanceof EvaluationEvent ev && ev.jobId().equals("job-1")));
    }

    @Test
    void banningARequiredFieldMeansStartDoesNotReproduce() {
        TaskResult r = executor.execute(task(List.of("currency")));
        assertEquals(TaskResult.Outcome.START_NOT_REPRODUCING, r.outcome());
        assertTrue(r.minimalFields().isEmpty());
    }

    @Test
    void simulatorOutageYieldsInconclusiveNotNotReproducing() throws IOException {
        sim.shutdown();
        TaskResult r = executor.execute(task(List.of()));
        assertEquals(TaskResult.Outcome.START_INCONCLUSIVE, r.outcome());
    }
}
