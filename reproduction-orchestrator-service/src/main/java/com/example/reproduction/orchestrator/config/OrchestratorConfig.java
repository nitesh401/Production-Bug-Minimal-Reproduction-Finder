package com.example.reproduction.orchestrator.config;

import com.example.reproduction.messaging.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class OrchestratorConfig {
    @Bean WebClient analysisWebClient(OrchestratorProperties p) { return WebClient.builder().baseUrl(p.analysisUrl()).build(); }

    // Partitions: 6 so that up to 6 worker threads (across instances) consume in parallel. Replication 1: local single broker.
    @Bean NewTopic jobsTopic() { return TopicBuilder.name(Topics.JOBS).partitions(3).replicas(1).build(); }
    @Bean NewTopic analysisTopic() { return TopicBuilder.name(Topics.ANALYSIS).partitions(3).replicas(1).build(); }
    @Bean NewTopic candidatesTopic() { return TopicBuilder.name(Topics.CANDIDATES).partitions(6).replicas(1).build(); }
    @Bean NewTopic candidatesRetryTopic() { return TopicBuilder.name(Topics.CANDIDATES_RETRY).partitions(6).replicas(1).build(); }
    @Bean NewTopic evaluationsTopic() { return TopicBuilder.name(Topics.EVALUATIONS).partitions(6).replicas(1).build(); }
    @Bean NewTopic resultsTopic() { return TopicBuilder.name(Topics.RESULTS).partitions(3).replicas(1).build(); }
    @Bean NewTopic dlqTopic() { return TopicBuilder.name(Topics.DLQ).partitions(1).replicas(1).build(); }
}
