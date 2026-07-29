package com.demo.listener;

import io.github.learnerview.simplydone4j.event.JobEventPublisher.JobPublishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DemoEventListener {
    private static final Logger log = LoggerFactory.getLogger(DemoEventListener.class);

    @EventListener
    public void onJobEvent(JobPublishedEvent event) {
        var data = event.data();
        switch (event.event()) {
            case JOB_CREATED ->
                log.info("EVENT JOB_CREATED: jobId={} type={} priority={}",
                        data.getJobId(), data.getJobType(), data.getPriority());
            case JOB_STARTED ->
                log.info("EVENT JOB_STARTED: jobId={} type={}", data.getJobId(), data.getJobType());
            case JOB_COMPLETED ->
                log.info("EVENT JOB_COMPLETED: jobId={} duration={}ms",
                        data.getJobId(), data.getDurationMs());
            case JOB_FAILED ->
                log.warn("EVENT JOB_FAILED: jobId={} result={}", data.getJobId(), data.getResult());
            case JOB_RETRY ->
                log.info("EVENT JOB_RETRY: jobId={} attempt={}/{}",
                        data.getJobId(), data.getAttempt(), data.getMaxAttempts());
            case JOB_CANCELLED ->
                log.info("EVENT JOB_CANCELLED: jobId={}", data.getJobId());
        }
    }
}
