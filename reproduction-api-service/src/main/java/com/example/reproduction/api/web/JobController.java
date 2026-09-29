package com.example.reproduction.api.web;

import com.example.reproduction.api.application.JobService;
import com.example.reproduction.api.dto.*;
import com.example.reproduction.domain.JobResult;
import com.example.reproduction.platform.logging.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reproduction/jobs")
public class JobController {
    private final JobService service;
    public JobController(JobService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<JobView> create(@Valid @RequestBody CreateJobRequest req,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String idemKey,
                                          HttpServletRequest http) {
        JobView v = service.create(req, idemKey, http.getHeader(CorrelationIdFilter.HEADER));
        return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/reproduction/jobs/" + v.jobId())).body(v);
    }

    @GetMapping("/{jobId}")
    public JobView get(@PathVariable String jobId) { return service.get(jobId); }

    @GetMapping("/{jobId}/progress")
    public JobProgressView progress(@PathVariable String jobId) { return service.progress(jobId); }

    @GetMapping("/{jobId}/result")
    public JobResult result(@PathVariable String jobId) { return service.result(jobId); }

    @GetMapping("/{jobId}/steps")
    public List<StepView> steps(@PathVariable String jobId) { return service.steps(jobId); }

    @GetMapping("/{jobId}/evaluations")
    public List<EvaluationView> evaluations(@PathVariable String jobId, @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        return service.evaluations(jobId, page, Math.min(size, 500));
    }

    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable String jobId, HttpServletRequest http) {
        service.cancel(jobId, http.getHeader(CorrelationIdFilter.HEADER));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{jobId}/resume")
    public ResponseEntity<Void> resume(@PathVariable String jobId, HttpServletRequest http) {
        service.resume(jobId, http.getHeader(CorrelationIdFilter.HEADER));
        return ResponseEntity.accepted().build();
    }
}
