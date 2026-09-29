package com.example.reproduction.analysis.messaging;

import com.example.reproduction.analysis.application.HistoryService;
import com.example.reproduction.messaging.AnalysisEvent;
import com.example.reproduction.messaging.Topics;
import com.example.reproduction.platform.kafka.PoisonMessageException;
import com.example.reproduction.platform.logging.Correlation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AnalysisEventListener {
    private static final Logger log = LoggerFactory.getLogger(AnalysisEventListener.class);
    private final HistoryService history;

    public AnalysisEventListener(HistoryService history) { this.history = history; }

    @KafkaListener(topics = Topics.ANALYSIS, groupId = "analysis-history",
            properties = "spring.json.value.default.type=com.example.reproduction.messaging.AnalysisEvent")
    public void onEvent(AnalysisEvent e) {
        if (e == null || e.jobId() == null || e.originalFields() == null || e.minimalCandidates() == null)
            throw new PoisonMessageException("malformed analysis event");
        try (Correlation c = Correlation.of(e.correlationId(), e.jobId(), null, null)) {
            boolean applied = history.record(e);
            log.info("analysis history {} for job", applied ? "updated" : "skipped (duplicate)");
        }
    }
}
