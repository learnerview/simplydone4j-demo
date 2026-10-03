package com.demo;

import io.github.learnerview.simplydone4j.dto.JobSubmissionRequest;
import io.github.learnerview.simplydone4j.handler.HandlerRegistry;
import io.github.learnerview.simplydone4j.service.JobSubmissionService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Smallest useful proof that the starter works end to end: register a handler,
 * submit one job, wait for the worker to run it, and assert the outcome.
 */
@Component
public class Demo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(Demo.class);
    private static final Set<String> TERMINAL = Set.of("SUCCESS", "FAILED", "DLQ", "CANCELLED");

    private final HandlerRegistry handlers;
    private final JobSubmissionService jobs;

    public Demo(HandlerRegistry handlers, JobSubmissionService jobs) {
        this.handlers = handlers;
        this.jobs = jobs;
    }

    @PostConstruct
    void registerHandler() {
        handlers.register("greet", ctx -> {
            log.info("HANDLER RAN jobId={} attempt={}/{} payload={}",
                    ctx.getJobId(), ctx.getAttemptCount() + 1, ctx.getMaxAttempts(), ctx.getPayload());
            String payload = ctx.getPayload() == null ? "" : ctx.getPayload();
            // `mode=fail` throws so the retry -> DLQ path is observable end to end;
            // `mode=slow` outlives a short timeoutSeconds so the watchdog fires.
            if (payload.contains("\"mode\":\"fail\"")) {
                throw new IllegalStateException("simulated handler failure");
            }
            if (payload.contains("\"mode\":\"slow\"")) {
                Thread.sleep(30_000L);
            }
            return "hello " + payload;
        });
    }

    @Override
    public void run(String... args) throws Exception {
        JobSubmissionRequest request = new JobSubmissionRequest();
        request.setJobType("greet");
        request.setIdempotencyKey("demo-1");
        request.setPriority("HIGH");
        request.setPayload(Map.of("name", "SimplyDone4J"));

        var submitted = jobs.submit("demo", request);
        log.info("SUBMITTED jobId={} status={} priority={}",
                submitted.getJobId(), submitted.getStatus(), submitted.getPriority());

        var jobId = submitted.getJobId();
        for (int i = 1; i <= 30; i++) {
            Thread.sleep(500);
            var job = jobs.getJob(jobId);
            log.info("POLL {} status={} attempt={}/{} result={}",
                    i, job.getStatus(), job.getAttemptCount(), job.getMaxAttempts(), job.getResult());
            if (TERMINAL.contains(job.getStatus())) {
                if ("SUCCESS".equals(job.getStatus())) {
                    log.info("VERIFIED job {} completed with result: {}", jobId, job.getResult());
                } else {
                    log.error("FAILED job {} ended as {}", jobId, job.getStatus());
                    System.exit(1);
                }
                return;
            }
        }
        log.error("TIMEOUT job {} never reached a terminal state", jobId);
        System.exit(1);
    }
}
