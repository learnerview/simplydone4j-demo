package com.demo.controller;

import io.github.learnerview.simplydone4j.dto.JobResponse;
import io.github.learnerview.simplydone4j.dto.JobSubmissionRequest;
import io.github.learnerview.simplydone4j.dto.JobSubmissionResponse;
import io.github.learnerview.simplydone4j.dto.QueueStatsResponse;
import io.github.learnerview.simplydone4j.service.JobSubmissionService;
import io.github.learnerview.simplydone4j.service.MonitoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobSubmissionService submissionService;
    private final MonitoringService monitoringService;

    public JobController(JobSubmissionService submissionService,
                         MonitoringService monitoringService) {
        this.submissionService = submissionService;
        this.monitoringService = monitoringService;
    }

    @PostMapping
    public ResponseEntity<JobSubmissionResponse> submit(@RequestParam(defaultValue = "demo-app") String producer,
                                                         @RequestBody JobSubmissionRequest request) {
        JobSubmissionResponse response = submissionService.submit(producer, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> getJob(@PathVariable String jobId) {
        return ResponseEntity.ok(submissionService.getJob(jobId));
    }

    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<Map<String, String>> cancel(@PathVariable String jobId) {
        submissionService.cancelJob(jobId);
        return ResponseEntity.ok(Map.of("status", "cancelled", "jobId", jobId));
    }

    @GetMapping("/stats")
    public ResponseEntity<QueueStatsResponse> stats() {
        return ResponseEntity.ok(monitoringService.getStats());
    }

    @GetMapping("/stats/status")
    public ResponseEntity<Map<String, Long>> statusCounts() {
        return ResponseEntity.ok(monitoringService.getCountByStatus());
    }

    @GetMapping("/stats/queues")
    public ResponseEntity<Map<String, Long>> queueDepths() {
        return ResponseEntity.ok(monitoringService.getQueueDepths());
    }
}
