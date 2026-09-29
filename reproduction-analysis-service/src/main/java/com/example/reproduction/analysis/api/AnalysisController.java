package com.example.reproduction.analysis.api;

import com.example.reproduction.analysis.application.AnalysisService;
import com.example.reproduction.analysis.application.HistoryService;
import com.example.reproduction.messaging.AnalysisRequest;
import com.example.reproduction.messaging.DependencyAnalysisView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {
    private final AnalysisService service;
    private final HistoryService history;

    public AnalysisController(AnalysisService service, HistoryService history) { this.service = service; this.history = history; }

    @PostMapping("/dependencies")
    public DependencyAnalysisView dependencies(@RequestBody AnalysisRequest req) {
        try { return service.analyze(req); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()); }
    }

    @GetMapping("/hints")
    public Map<String, Double> hints(@RequestParam List<String> fields) { return history.keepHints(fields); }

    @GetMapping("/history")
    public Map<String, Object> fieldHistory(@RequestParam String field) {
        return Map.of("field", field, "seen", history.seen(field), "kept", history.kept(field),
                "keepLikelihood", history.keepLikelihood(field) == null ? -1 : history.keepLikelihood(field));
    }
}
