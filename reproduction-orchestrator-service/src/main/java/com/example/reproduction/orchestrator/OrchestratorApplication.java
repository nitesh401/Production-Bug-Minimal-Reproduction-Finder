package com.example.reproduction.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EntityScan("com.example.reproduction.persistence.entity")
@EnableJpaRepositories("com.example.reproduction.persistence.repository")
public class OrchestratorApplication {
    public static void main(String[] args) { SpringApplication.run(OrchestratorApplication.class, args); }
}
