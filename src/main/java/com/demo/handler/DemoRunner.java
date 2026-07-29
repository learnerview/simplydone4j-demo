package com.demo.handler;

import io.github.learnerview.simplydone4j.dto.JobSubmissionRequest;
import io.github.learnerview.simplydone4j.dto.JobSubmissionResponse;
import io.github.learnerview.simplydone4j.exception.RateLimitExceededException;
import io.github.learnerview.simplydone4j.service.JobSubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class DemoRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoRunner.class);

    private final JobSubmissionService submissionService;

    public DemoRunner(JobSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @Override
    public void run(String... args) {
        log.info("=== Submitting demo jobs ===");

        submit("quick-success", "demo-1", "HIGH",
                Map.of("task", "email-notification", "to", "user@example.com"));

        submit("quick-success", "demo-2", "NORMAL",
                Map.of("task", "data-sync", "source", "db", "target", "cache"));

        submit("quick-success", "demo-3", "LOW",
                Map.of("task", "cleanup-logs", "retention", "30d"));

        submit("failing-task", "demo-fail-1", "NORMAL", null);

        submit("failing-task", "demo-fail-2", "NORMAL", null);

        submit("callback-test", "demo-cb-1", "NORMAL", null, "http://localhost:8080/api/jobs/callback-handler");

        JobSubmissionRequest delayed = new JobSubmissionRequest();
        delayed.setJobType("quick-success");
        delayed.setIdempotencyKey("demo-delayed-1");
        delayed.setPriority("NORMAL");
        delayed.setPayload(Map.of("task", "delayed-task"));
        delayed.setNextRunAt(Instant.now().plusSeconds(30));
        doSubmit("delayed-app", delayed);

        JobSubmissionRequest quickTimeout = new JobSubmissionRequest();
        quickTimeout.setJobType("long-running");
        quickTimeout.setIdempotencyKey("demo-timeout-1");
        quickTimeout.setTimeoutSeconds(3);
        doSubmit("timeout-app", quickTimeout);

        log.info("=== Idempotency test (submitting same key again) ===");
        submit("quick-success", "demo-1", "HIGH", null);

        log.info("=== Rate limit test (submitting bursts) ===");
        for (int i = 0; i < 5; i++) {
            try {
                submit("quick-success", "burst-" + i, "NORMAL", null);
            } catch (RateLimitExceededException e) {
                log.warn("Rate limited after {} submissions: retry after {}s", i, e.getRetryAfterSeconds());
                break;
            }
        }

        log.info("=== Demo jobs submitted. Use the REST API at http://localhost:8080/api/jobs/ ===");
        log.info("  GET  /api/jobs/{jobId}       — view job details");
        log.info("  POST /api/jobs/{jobId}/cancel — cancel a queued job");
        log.info("  GET  /api/jobs/stats          — queue statistics");
        log.info("  GET  /api/jobs/stats/status   — counts by status");
        log.info("  GET  /api/jobs/stats/queues   — queue depths by priority");
    }

    private void submit(String jobType, String idempotencyKey, String priority, Map<String, Object> payload) {
        submit(jobType, idempotencyKey, priority, payload, null);
    }

    private void submit(String jobType, String idempotencyKey, String priority,
                        Map<String, Object> payload, String callbackUrl) {
        JobSubmissionRequest req = new JobSubmissionRequest();
        req.setJobType(jobType);
        req.setIdempotencyKey(idempotencyKey);
        req.setPriority(priority);
        req.setPayload(payload);
        req.setCallbackUrl(callbackUrl);
        doSubmit("demo-app", req);
    }

    private void doSubmit(String producer, JobSubmissionRequest req) {
        try {
            JobSubmissionResponse res = submissionService.submit(producer, req);
            log.info("Submitted jobId={} type={} status={} priority={}",
                    res.getJobId(), res.getJobType(), res.getStatus(), res.getPriority());
        } catch (Exception e) {
            log.warn("Failed to submit {}: {}", req.getIdempotencyKey(), e.getMessage());
        }
    }
}
