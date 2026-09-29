package com.example.reproduction.worker.oracle;

import com.example.reproduction.domain.*;
import com.example.reproduction.exception.EvaluationInfrastructureException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpBugOracleTest {
    MockWebServer server;
    HttpBugOracle oracle;
    final Candidate candidate = Candidate.of(Map.of("amount", 15000));

    @BeforeEach
    void start() throws IOException {
        server = new MockWebServer();
        server.start();
        oracle = new HttpBugOracle(WebClient.create(server.url("/").toString()), new ObjectMapper(),
                BugSignature.statusAndCode(500, "PAYMENT_ROUTE_FAILURE"), "SIMPLE_AND", Duration.ofMillis(400));
    }

    @AfterEach
    void stop() throws IOException { server.shutdown(); }

    @Test
    void matchingSignatureMeansReproduces() {
        server.enqueue(new MockResponse().setResponseCode(500).setHeader("Content-Type", "application/json").setBody("{\"errorCode\":\"PAYMENT_ROUTE_FAILURE\"}"));
        assertEquals(EvaluationStatus.REPRODUCES_BUG, oracle.evaluate(candidate, 0).status());
    }

    @Test
    void differentErrorCodeOrOkMeansDoesNotReproduce() {
        server.enqueue(new MockResponse().setResponseCode(500).setHeader("Content-Type", "application/json").setBody("{\"errorCode\":\"OTHER\"}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"message\":\"OK\"}"));
        assertEquals(EvaluationStatus.DOES_NOT_REPRODUCE, oracle.evaluate(candidate, 0).status());
        assertEquals(EvaluationStatus.DOES_NOT_REPRODUCE, oracle.evaluate(candidate, 0).status());
    }

    @Test
    void gatewayErrorIsInfrastructureNotBugGone() {
        server.enqueue(new MockResponse().setResponseCode(503));
        EvaluationInfrastructureException e = assertThrows(EvaluationInfrastructureException.class, () -> oracle.evaluate(candidate, 0));
        assertEquals(EvaluationStatus.SYSTEM_ERROR, e.status());
    }

    @Test
    void slowResponseIsTimeout() {
        server.enqueue(new MockResponse().setResponseCode(200).setBodyDelay(3, java.util.concurrent.TimeUnit.SECONDS).setBody("{}"));
        EvaluationInfrastructureException e = assertThrows(EvaluationInfrastructureException.class, () -> oracle.evaluate(candidate, 0));
        assertEquals(EvaluationStatus.TIMEOUT, e.status());
    }

    @Test
    void unreachableSimulatorIsSystemError() throws IOException {
        server.shutdown();
        EvaluationInfrastructureException e = assertThrows(EvaluationInfrastructureException.class, () -> oracle.evaluate(candidate, 0));
        assertTrue(e.status() == EvaluationStatus.SYSTEM_ERROR || e.status() == EvaluationStatus.TIMEOUT);
    }

    @Test
    void candidateHashAttemptAndScenarioAreSentAsHeaders() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        oracle.evaluate(candidate, 2);
        var req = server.takeRequest();
        assertEquals("2", req.getHeader("X-Attempt"));
        assertEquals("SIMPLE_AND", req.getHeader("X-Bug-Scenario"));
        assertEquals(candidate.hash(), req.getHeader("X-Candidate-Hash"));
        assertEquals("/simulate/payment", req.getPath());
    }
}
