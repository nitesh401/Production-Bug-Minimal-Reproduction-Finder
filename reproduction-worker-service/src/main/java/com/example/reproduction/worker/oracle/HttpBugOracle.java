package com.example.reproduction.worker.oracle;

import com.example.reproduction.domain.*;
import com.example.reproduction.evaluation.BugOracle;
import com.example.reproduction.evaluation.BugSignatureEvaluator;
import com.example.reproduction.exception.EvaluationInfrastructureException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Exceptions;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Calls the (simulated) production endpoint with WebClient. Classification rules, the crux of correctness:
 * <ul>
 *   <li>the service ANSWERED (any status except 502/503/504) -> the signature decides REPRODUCES / DOES_NOT_REPRODUCE;</li>
 *   <li>connection problems / gateway errors -> SYSTEM_ERROR; client-side timeout -> TIMEOUT;
 *       both are thrown as {@link EvaluationInfrastructureException} and NEVER mean "bug gone".</li>
 * </ul>
 * Blocking on purpose: evaluations run on the bounded evaluation pool, never on a reactive event loop.
 */
public final class HttpBugOracle implements BugOracle {
    private final WebClient client;
    private final ObjectMapper mapper;
    private final BugSignatureEvaluator signature;
    private final String scenarioId;
    private final Duration timeout;

    public HttpBugOracle(WebClient client, ObjectMapper mapper, BugSignature signature, String scenarioId, Duration timeout) {
        this.client = client; this.mapper = mapper; this.signature = new BugSignatureEvaluator(signature);
        this.scenarioId = scenarioId; this.timeout = timeout;
    }

    private record Raw(int status, String body) {}

    @Override
    public EvaluationResult evaluate(Candidate candidate, int attempt) {
        long t0 = System.nanoTime();
        Raw raw;
        try {
            raw = client.post().uri("/simulate/payment")
                    .header("X-Bug-Scenario", scenarioId == null ? "" : scenarioId)
                    .header("X-Attempt", Integer.toString(attempt))
                    .header("X-Candidate-Hash", candidate.hash())
                    .bodyValue(candidate.nested())
                    .exchangeToMono(r -> r.bodyToMono(String.class).defaultIfEmpty("").map(b -> new Raw(r.statusCode().value(), b)))
                    .timeout(timeout)
                    .block();
        } catch (RuntimeException e) {
            Throwable cause = Exceptions.unwrap(e);
            if (cause instanceof TimeoutException || cause.getCause() instanceof TimeoutException)
                throw new EvaluationInfrastructureException(EvaluationStatus.TIMEOUT, "no answer within " + timeout, cause);
            if (cause instanceof WebClientRequestException)
                throw new EvaluationInfrastructureException(EvaluationStatus.SYSTEM_ERROR, "simulator unreachable: " + cause.getMessage(), cause);
            throw new EvaluationInfrastructureException(EvaluationStatus.SYSTEM_ERROR, "unexpected client failure: " + cause, cause);
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        if (raw == null) throw new EvaluationInfrastructureException(EvaluationStatus.INCONCLUSIVE, "empty exchange", null);
        if (raw.status() == 502 || raw.status() == 503 || raw.status() == 504)
            throw new EvaluationInfrastructureException(EvaluationStatus.SYSTEM_ERROR, "gateway/availability status " + raw.status(), null);

        String errorCode = null;
        try {
            JsonNode n = mapper.readTree(raw.body());
            if (n != null && n.hasNonNull("errorCode")) errorCode = n.get("errorCode").asText();
        } catch (Exception ignored) { /* non-JSON body: errorCode stays null, body pattern may still match */ }
        boolean bug = signature.matches(new ResponseObservation(raw.status(), errorCode, raw.body(), ms, null));
        return EvaluationResult.single(bug ? EvaluationStatus.REPRODUCES_BUG : EvaluationStatus.DOES_NOT_REPRODUCE, ms,
                "HTTP " + raw.status() + (errorCode == null ? "" : " " + errorCode));
    }
}
