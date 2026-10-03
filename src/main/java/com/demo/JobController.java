package com.demo;

import io.github.learnerview.simplydone4j.dto.JobResponse;
import io.github.learnerview.simplydone4j.dto.JobSubmissionRequest;
import io.github.learnerview.simplydone4j.dto.JobSubmissionResponse;
import io.github.learnerview.simplydone4j.service.JobSubmissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends real input into the engine so each lifecycle path can be exercised.
 *
 * <pre>
 * curl -X POST 'localhost:8080/jobs?name=Ada&amp;priority=HIGH&amp;mode=ok'
 * curl -X POST 'localhost:8080/jobs?name=Bad&amp;mode=fail'
 * curl -X POST 'localhost:8080/jobs?name=D&amp;delaySeconds=30'
 * curl -X POST 'localhost:8080/jobs?name=T&amp;timeoutSeconds=1'
 * curl localhost:8080/jobs/&lt;jobId&gt;
 * </pre>
 */
@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobSubmissionService jobs;

    public JobController(JobSubmissionService jobs) {
        this.jobs = jobs;
    }

    @PostMapping
    public ResponseEntity<JobSubmissionResponse> submit(
            @RequestParam(defaultValue = "world") String name,
            @RequestParam(defaultValue = "NORMAL") String priority,
            @RequestParam(defaultValue = "ok") String mode,
            @RequestParam(required = false) String key,
            @RequestParam(required = false) Integer delaySeconds,
            @RequestParam(required = false) Integer timeoutSeconds,
            @RequestBody(required = false) Map<String, Object> extra) {

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("mode", mode);
        if (extra != null) {
            payload.put("extra", extra);
        }

        JobSubmissionRequest request = new JobSubmissionRequest();
        request.setJobType("greet");
        request.setIdempotencyKey(key != null ? key : "demo-" + name + "-" + System.nanoTime());
        request.setPriority(priority);
        request.setPayload(payload);
        if (delaySeconds != null) {
            request.setNextRunAt(Instant.now().plusSeconds(delaySeconds));
        }
        if (timeoutSeconds != null) {
            request.setTimeoutSeconds(timeoutSeconds);
        }

        return ResponseEntity.accepted().body(jobs.submit("demo-api", request));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> get(@PathVariable String jobId) {
        return ResponseEntity.ok(jobs.getJob(jobId));
    }
}
