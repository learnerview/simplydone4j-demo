package com.demo.handler;

import io.github.learnerview.simplydone4j.handler.HandlerRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DemoHandlers {
    private static final Logger log = LoggerFactory.getLogger(DemoHandlers.class);

    private final HandlerRegistry registry;

    public DemoHandlers(HandlerRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    void register() {
        registry.register("quick-success", ctx -> {
            log.info("[quick-success] Job {} started (attempt {}/{})",
                    ctx.getJobId(), ctx.getAttemptCount() + 1, ctx.getMaxAttempts());
            log.info("[quick-success] Payload: {}", ctx.getPayload());
            return "ok";
        });

registry.register("failing-task", ctx -> {
            log.warn("[failing-task] Job {} about to fail (attempt {}/{})",
                    ctx.getJobId(), ctx.getAttemptCount() + 1, ctx.getMaxAttempts());
            throw new RuntimeException("Simulated failure in failing-task handler");
        });

        registry.register("long-running", ctx -> {
            int timeout = 10;
            log.info("[long-running] Job {} will run for {}s (attempt {}/{})",
                    ctx.getJobId(), timeout, ctx.getAttemptCount() + 1, ctx.getMaxAttempts());
            Thread.sleep(timeout * 1000L);
            log.info("[long-running] Job {} completed", ctx.getJobId());
            return "completed";
        });

        registry.register("callback-test", ctx -> {
            log.info("[callback-test] Job {} executing — callback URL will fire on completion",
                    ctx.getJobId());
            return "callback-dispatched";
        });

        log.info("All demo handlers registered successfully");
    }
}
