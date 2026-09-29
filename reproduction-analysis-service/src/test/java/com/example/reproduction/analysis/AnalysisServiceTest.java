package com.example.reproduction.analysis;

import com.example.reproduction.analysis.application.AnalysisService;
import com.example.reproduction.analysis.application.HistoryService;
import com.example.reproduction.messaging.AnalysisRequest;
import com.example.reproduction.messaging.DependencyAnalysisView;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisServiceTest {
    @Test
    void groupsAmountAndCurrencyAndReportsCriticalFields() {
        HistoryService history = Mockito.mock(HistoryService.class);
        Mockito.when(history.keepHints(Mockito.anyCollection())).thenReturn(Map.of("amount", 0.9));
        AnalysisService svc = new AnalysisService(history, new SimpleMeterRegistry());
        DependencyAnalysisView v = svc.analyze(new AnalysisRequest(Map.of("amount", 1, "currency", "INR", "taxType", "GST", "coupon", "X"), null, null));
        assertEquals(List.of("amount", "coupon", "currency", "taxType"), v.fields());
        assertEquals(1, v.stronglyCoupledGroups().size());
        assertTrue(v.criticalFields().contains("amount"));
        assertEquals(0.9, v.keepHints().get("amount"), 1e-9);
    }

    @Test
    void rejectsEmptyRequest() {
        AnalysisService svc = new AnalysisService(Mockito.mock(HistoryService.class), new SimpleMeterRegistry());
        assertThrows(IllegalArgumentException.class, () -> svc.analyze(new AnalysisRequest(null, null, null)));
    }
}
