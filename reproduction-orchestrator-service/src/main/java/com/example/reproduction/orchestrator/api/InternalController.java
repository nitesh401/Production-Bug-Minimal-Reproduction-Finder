package com.example.reproduction.orchestrator.api;

import com.example.reproduction.messaging.JobContext;
import com.example.reproduction.orchestrator.application.JobContextService;
import com.example.reproduction.persistence.entity.WorkerTaskEntity;
import com.example.reproduction.persistence.repository.WorkerTaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Internal endpoints (service-to-service only; not exposed by the API service). */
@RestController
@RequestMapping("/internal/jobs")
public class InternalController {
    private final JobContextService contexts;
    private final WorkerTaskRepository tasks;

    public InternalController(JobContextService contexts, WorkerTaskRepository tasks) { this.contexts = contexts; this.tasks = tasks; }

    @GetMapping("/{jobId}/context")
    public JobContext context(@PathVariable String jobId) { return contexts.get(jobId); }

    @GetMapping("/{jobId}/tasks")
    public List<Map<String, Object>> tasks(@PathVariable String jobId) {
        return tasks.findByJobId(jobId).stream().map(t -> Map.<String, Object>of("taskId", t.taskId, "type", t.type, "status", t.status.name(),
                "attempt", t.attempt, "banned", t.bannedJson)).toList();
    }

    @ExceptionHandler(JobContextService.JobNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(JobContextService.JobNotFoundException e) { return Map.of("error", e.getMessage()); }
}
