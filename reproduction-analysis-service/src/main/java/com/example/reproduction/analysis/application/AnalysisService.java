package com.example.reproduction.analysis.application;

import com.example.reproduction.algorithm.graph.DefaultDependencyRules;
import com.example.reproduction.algorithm.graph.DependencyAnalysis;
import com.example.reproduction.algorithm.graph.DependencyAnalyzer;
import com.example.reproduction.messaging.AnalysisRequest;
import com.example.reproduction.messaging.DependencyAnalysisView;
import com.example.reproduction.util.InputFlattener;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class AnalysisService {
    private final DependencyAnalyzer analyzer = new DependencyAnalyzer(DefaultDependencyRules.paymentDomain());
    private final HistoryService history;
    private final MeterRegistry meters;

    public AnalysisService(HistoryService history, MeterRegistry meters) { this.history = history; this.meters = meters; }

    /** O(V + E) for graph construction, Tarjan SCC, weak components; O(C*(C/64+E)) for criticality. */
    public DependencyAnalysisView analyze(AnalysisRequest req) {
        List<String> paths = paths(req);
        return Timer.builder("analysis_duration").register(meters).record(() -> {
            DependencyAnalysis a = analyzer.analyze(paths, req.explicitDependencies());
            return new DependencyAnalysisView(paths, a.dependencies(), a.stronglyCoupledGroups(), a.independentGroups(),
                    a.criticalFields(10), a.condensation().count(), a.weakComponents().count(), history.keepHints(paths));
        });
    }

    private static List<String> paths(AnalysisRequest req) {
        if (req.fields() != null && !req.fields().isEmpty()) return req.fields().stream().distinct().sorted().toList();
        if (req.input() == null || req.input().isEmpty()) throw new IllegalArgumentException("Provide 'input' or 'fields'");
        Map<String, Object> flat = new TreeMap<>(InputFlattener.flatten(req.input()));
        return new ArrayList<>(flat.keySet());
    }
}
