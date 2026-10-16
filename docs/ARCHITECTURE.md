# Architecture

`simplydone4j-demo` is a **thin consumer** of the SimplyDone4J Spring Boot
starter. It adds no engine logic of its own: it registers a handler, submits
jobs, and exposes a small HTTP facade. All scheduling, retries, leasing,
dead-lettering, health, and metrics come from
`io.github.learnerview:simplydone4j-spring-boot-starter:2.1.0`.

## Components

```text
                         ┌─────────────────────────────────────────┐
        HTTP client ───▶  │  JobController        (@RestController)  │
                         │  POST /jobs   GET /jobs/{jobId}          │
                         └───────────────────┬─────────────────────┘
                                             │ JobSubmissionService
                                             ▼
   ┌────────────────────────────┐   ┌───────────────────────────┐
   │ Demo (CommandLineRunner)   │──▶│  SimplyDone4J engine       │──▶ Redis
   │ startup smoke test         │   │  (starter 2.1.0)           │
   │ registers "greet" handler  │   │  queues, jobs, scheduler,  │
   └────────────────────────────┘   │  workers, retry, DLQ       │
                                     └───────────────────────────┘
```

### Source files (`src/main/java/com/demo/`)

- **`DemoApplication.java`** — `@SpringBootApplication` entry point; calls
  `SpringApplication.run`.
- **`JobController.java`** — `@RestController` at `/jobs`. Builds a
  `JobSubmissionRequest` from query params (`name`, `priority`, `mode`, `key`,
  `delaySeconds`, `timeoutSeconds`) plus an optional JSON body, then calls
  `JobSubmissionService.submit("demo-api", request)` and returns
  `202 Accepted`. `GET /jobs/{jobId}` returns the library `JobResponse`.
  Defaults: `name=world`, `priority=NORMAL`, `mode=ok`; generated idempotency
  key is `demo-<name>-<nanoTime>` when `key` is absent.
- **`Demo.java`** — a `CommandLineRunner`. In `@PostConstruct` it registers a
  `greet` handler. In `run(...)` it submits one job (`priority=HIGH`, key
  `demo-1`, payload `{"name":"SimplyDone4J"}`), polls `getJob` up to 30 times
  at 500 ms intervals, and asserts `SUCCESS`. On a non-success terminal state
  or timeout it logs an error and calls `System.exit(1)`.

The `greet` handler returns `"hello " + payload`. To exercise failure paths it
inspects the payload: `"mode":"fail"` throws `IllegalStateException`
(retry → DLQ), and `"mode":"slow"` sleeps 30 s (timeout watchdog).

## Job lifecycle

Status values are the library's `JobStatus` enum:
`QUEUED` → `RUNNING` → (`RETRY_SCHEDULED` → …) → one of
`SUCCESS`, `FAILED`, `CANCELLED`, `DLQ`.

```text
submit ─▶ QUEUED ─▶ RUNNING ─▶ SUCCESS
                      │
                      ├─ handler throws ─▶ RETRY_SCHEDULED ─▶ (retry) …
                      │                          │ attempts exhausted
                      │                          ▼
                      └────────────────────────▶ DLQ
```

- Retries: `simplydone4j.retry.max-attempts` (default 3) with
  `initial-delay-seconds` (default 1).
- Delayed jobs: `JobSubmissionRequest.nextRunAt` set from `delaySeconds`; the
  scheduler (`simplydone4j.scheduler.enabled=true`) promotes them.
- Timeouts: `JobSubmissionRequest.timeoutSeconds`.

## Data model

The only persisted state is in Redis. Keys are namespaced by
`simplydone4j.key-prefix` (this app: `demo`). Queue names, key layout, and job
serialization are owned by the library — this repo does not define them. There
is no SQL/NoSQL database and no external message broker.

## Observability

- **Health** — `SimplyDoneHealthAutoConfiguration` contributes a
  `simplyDoneHealthIndicator`, surfaced under `.components.simplyDone`. It
  reports `queueDepths` (per priority), `totalQueued`, `running`,
  `deadLettered`, `deadLetterThreshold`, `maxQueueDepth`, and reports `DOWN`
  when `deadLettered >= dead-letter-threshold` or
  `totalQueued > max-queue-depth`.
- **Metrics** — `SimplyDoneMetricsAutoConfiguration` registers Micrometer
  meters prefixed `simplydone4j` (see README for the verified list).

## Deployment topology

Single JVM + one Redis. `Dockerfile` is a multi-stage build; `docker-compose.yml`
runs `redis:7-alpine` (with a ping healthcheck) and the app, injecting
`REDIS_URL=redis://redis:6379`. See
[`DEPLOYMENT.md`](DEPLOYMENT.md).

## What this demo is *not*

It is not a reference production architecture: no auth, no TLS, no HA Redis, no
horizontal-worker tuning, and no automated tests. See the README's
[Limitations & Known Issues](../README.md#limitations--known-issues) and
[`PRODUCTION_READINESS.md`](PRODUCTION_READINESS.md).
