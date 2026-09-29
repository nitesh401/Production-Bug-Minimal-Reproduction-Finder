package com.example.reproduction.orchestrator.application;

import com.example.reproduction.messaging.AnalysisRequest;
import com.example.reproduction.messaging.DependencyAnalysisView;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Synchronous REST call to the analysis service (request/response, small payload, idempotent).
 * If the analysis service stays unavailable we DEGRADE: no explicit dependencies and no priority hints. The worker
 * still applies its built-in default rules, so the search remains correct, just less informed. Counted in a metric.
 */
@Component
public class AnalysisClient {
    private static final Logger log = LoggerFactory.getLogger(AnalysisClient.class);
    private final WebClient client;
    private final MeterRegistry meters;

    public AnalysisClient(WebClient analysisWebClient, MeterRegistry meters) { this.client = analysisWebClient; this.meters = meters; }

    public DependencyAnalysisView analyze(Map<String, Object> input) {
        try {
            return client.post().uri("/api/v1/analysis/dependencies").bodyValue(new AnalysisRequest(input, null, null))
                    .retrieve().bodyToMono(DependencyAnalysisView.class)
                    .retryWhen(Retry.backoff(3, Duration.ofMillis(300)).filter(t -> !(t instanceof org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest)))
                    .timeout(Duration.ofSeconds(15)).block();
        } catch (RuntimeException e) {
            meters.counter("analysis.degraded").increment();
            log.warn("analysis service unavailable, continuing without explicit dependencies/hints: {}", e.toString());
            return new DependencyAnalysisView(List.of(), List.of(), List.of(), List.of(), List.of(), 0, 0, Map.of());
        }
    }
}
