package com.example.reproduction.worker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WorkerConfig {
    @Bean
    WebClient simulatorWebClient(WorkerProperties p) { return WebClient.builder().baseUrl(p.simulatorUrl()).build(); }

    @Bean
    WebClient orchestratorWebClient(WorkerProperties p) { return WebClient.builder().baseUrl(p.orchestratorUrl()).build(); }
}
