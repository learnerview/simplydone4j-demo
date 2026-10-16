# SimplyDone4J Demo

Minimal Spring Boot sample application that exercises the
[SimplyDone4J](https://github.com/learnerview/simplydone4j) job-scheduling
starter against a real Redis instance.

> **This is a demo / sample application, not a production-hardened service.**
> It exists to prove the starter works end to end and to make each lifecycle
> behavior easy to observe. See [Limitations & Known Issues](#limitations--known-issues).

## Overview

The app registers one handler (`greet`), submits a smoke-test job on startup,
polls it to a terminal state, and exposes the same submission service over a
small HTTP API (`POST /jobs`, `GET /jobs/{jobId}`). It demonstrates:

- registering a handler with the starter's `HandlerRegistry`;
- submitting jobs with priority, idempotency key, delay, and timeout;
- retry -> dead-letter (DLQ) behavior;
- delayed execution and handler timeouts;
- job status polling through the library's `JobSubmissionService`;
- the starter's health contributor and Micrometer metrics via Actuator.

It consumes the library, it does not implement it. Everything queue-related is
provided by `io.github.learnerview:simplydone4j-spring-boot-starter:2.1.0`
(resolved from Maven Central).

## Features

- **Startup smoke test** — `Demo` registers `greet`, submits one job
  (`priority=HIGH`, idempotency key `demo-1`), polls up to 30 × 500 ms, and
  exits non-zero if the job does not reach `SUCCESS`.
- **HTTP submission API** — `POST /jobs` accepts `name`, `priority`, `mode`,
  `key`, `delaySeconds`, `timeoutSeconds` plus an optional JSON body.
- **Status API** — `GET /jobs/{jobId}` returns the current job state.
- **Failure paths** — `mode=fail` throws to trigger retries and the DLQ path;
  `mode=slow` sleeps 30 s so a short `timeoutSeconds` trips the watchdog.
- **Observability** — Actuator exposes `health` and `metrics`, including the
  starter's `simplyDone` health component and `simplydone4j.*` meters.
- **Containerized demo** — multi-stage `Dockerfile` and `docker-compose.yml`
  that bring up Redis + the app together.

## Architecture

A single Spring Boot process (Java 21) that talks to Redis. No database, no
other services, no message broker. Details, including the request/job
lifecycle, are in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

```text
client ──HTTP──▶ JobController ──▶ JobSubmissionService ──▶ Redis (queues + job state)
                                        ▲
Demo.CommandLineRunner ─────────────────┘        worker/scheduler polls Redis
```

## Tech Stack

| Component | Version / detail | Source |
|-----------|------------------|--------|
| Java | 21 (`<java.version>21</java.version>`) | `pom.xml` |
| Spring Boot | 4.1.0 (parent) | `pom.xml` |
| SimplyDone4J starter | 2.1.0 | `pom.xml` |
| Web | `spring-boot-starter-web` | `pom.xml` |
| Actuator | `spring-boot-starter-actuator` | `pom.xml` |
| Redis | 7 (`redis:7-alpine` in Compose) | `docker-compose.yml` |
| Build | Maven (`spring-boot-maven-plugin`) | `pom.xml` |

## Prerequisites

- **JDK 21** — the project compiles with `release 21`. (A newer JDK on `PATH`
  is fine as long as Maven runs on 21; this repo was verified with Maven
  3.9.6 / JDK 21.0.7.)
- **Maven** — 3.9.x used here. The POM does not enforce a Maven version.
- **Docker + Docker Compose** — for the recommended containerized run and to
  provide Redis.
- **Redis 7+** — reachable by the app; the starter has no in-memory fallback.
- **curl** and optionally **jq** — for the HTTP examples (optional).

## Quick Start

### Docker Compose (recommended)

```bash
docker compose up --build
```

This starts Redis (with a `redis-cli ping` healthcheck) and the demo, submits
one job on startup, and logs the result. Look for:

```text
VERIFIED job <jobId> completed with result: hello {"name":"SimplyDone4J"}
```

To expose the API on a different host port (default `8080`):

```bash
DEMO_PORT=8082 docker compose up --build
```

### Run locally

```bash
# 1. Redis (the only external dependency)
docker run -d --name redis -p 6379:6379 redis:7-alpine

# 2. Build and run the demo. The starter 2.1.0 is fetched from Maven Central.
mvn clean package
java -jar target/simplydone4j-demo-1.0.0.jar
```

Redis is required: the starter has no in-memory fallback. The app exits
non-zero if the startup smoke-test job does not reach a terminal state, so the
process itself doubles as a smoke test.

## Configuration

All values come from `src/main/resources/application.yml` (and
`docker-compose.yml` for the container). Full reference:
[`docs/CONFIGURATION.md`](docs/CONFIGURATION.md).

| Name | Purpose | Default | Required |
|------|---------|---------|----------|
| `REDIS_URL` (env) → `spring.data.redis.url` | Redis connection URL | `redis://localhost:6379` | Yes (Redis must be reachable) |
| `simplydone4j.key-prefix` | Redis key namespace for this app | `demo` | No |
| `simplydone4j.scheduler.enabled` | Enable the delayed/retry scheduler | `true` | No |
| `simplydone4j.monitoring.enabled` | Enable health + metrics instrumentation | `true` | No |
| `simplydone4j.retry.max-attempts` | Max handler attempts before DLQ | `3` | No |
| `simplydone4j.retry.initial-delay-seconds` | Base retry backoff delay | `1` | No |
| `management.endpoints.web.exposure.include` | Exposed Actuator endpoints | `health,metrics` | No |
| `management.endpoint.health.show-details` | Health detail verbosity | `always` | No |
| `server.port` | HTTP port | `8080` | No |
| `server.shutdown` | Shutdown mode | `graceful` | No |
| `spring.lifecycle.timeout-per-shutdown-phase` | Graceful shutdown window | `30s` | No |
| `DEMO_PORT` (env, Compose only) | Host port mapped to container `8080` | `8080` | No |

The following keys are **library defaults**, not set in this repo's
`application.yml` (verified by decompiling starter 2.1.0):

| Name | Purpose | Library default |
|------|---------|-----------------|
| `simplydone4j.health.dead-letter-threshold` | DLQ count that flips health to `DOWN`; `0` disables | `1` |
| `simplydone4j.health.max-queue-depth` | Total queued count that flips health to `DOWN`; `0` disables | `100000` |
| `simplydone4j.metrics.queue-depth-refresh-seconds` | Queue-depth gauge refresh interval | see library docs |

## Usage

Submit a job (the response contains the job ID; persist it and poll rather than
blocking the request thread):

```bash
# Normal job with a stable idempotency key
curl -X POST "http://localhost:8080/jobs?name=Ada&priority=HIGH&key=order-123"

# Submitting the same key again returns the existing job
curl -X POST "http://localhost:8080/jobs?name=Ada&priority=HIGH&key=order-123"

# Handler failure: retries, then FAILED/DLQ per configuration
curl -X POST "http://localhost:8080/jobs?name=Bad&mode=fail&key=failure-1"

# Delayed execution and a handler timeout
curl -X POST "http://localhost:8080/jobs?name=Later&delaySeconds=5&key=delayed-1"
curl -X POST "http://localhost:8080/jobs?name=Slow&mode=slow&timeoutSeconds=1&key=timeout-1"

# Poll a returned job ID
curl "http://localhost:8080/jobs/<jobId>"
```

`POST /jobs` returns `202 Accepted` with a `JobSubmissionResponse`. If `key`
is omitted, the demo generates `demo-<name>-<nanoTime>`.

Job status values (library `JobStatus` enum): `QUEUED`, `RUNNING`,
`RETRY_SCHEDULED`, `SUCCESS`, `FAILED`, `CANCELLED`, `DLQ`.

### Observability endpoints

```bash
# Starter health contributor
curl -s http://localhost:8080/actuator/health | jq '.components.simplyDone'

# simplydone4j meters
curl -s http://localhost:8080/actuator/metrics | jq '.names[] | select(startswith("simplydone4j"))'

# Per-outcome counters
curl -s "http://localhost:8080/actuator/metrics/simplydone4j.jobs.completed?tag=outcome:SUCCESS"
```

Verified meter names registered by starter 2.1.0 (12 distinct names):

`simplydone4j.jobs.submitted`, `simplydone4j.jobs.claimed`,
`simplydone4j.fencing.rejections`, `simplydone4j.lease.reaped`,
`simplydone4j.jobs.overlapped`, `simplydone4j.scheduler.poll`,
`simplydone4j.scheduler.claims.per.poll`, `simplydone4j.jobs.submitted.by.priority`,
`simplydone4j.jobs.completed`, `simplydone4j.jobs.duration`,
`simplydone4j.queue.depth`, `simplydone4j.jobs.dead.letter`.

The `simplyDone` health component reports `queueDepths` (per priority),
`totalQueued`, `running`, `deadLettered`, `deadLetterThreshold`, and
`maxQueueDepth`. It reports `DOWN` when `deadLettered >= dead-letter-threshold`
or `totalQueued > max-queue-depth`; setting either threshold to `0` disables
that check (verified against the 2.1.0 bytecode).

### Usage guidelines

- Use a stable idempotency key derived from the business operation
  (e.g. `order-123-email`), never a random UUID regenerated per retry.
- Keep handlers idempotent: a retry can occur after a side effect but before
  the result is recorded.
- Return the job ID quickly; poll or consume lifecycle events instead of
  waiting inside an HTTP request.
- Set `timeoutSeconds` for work with a known upper bound, and make handlers
  honor interruption during blocking I/O.
- Treat `FAILED` and `DLQ` as distinct outcomes; alert on DLQ growth.
- Use a distinct `simplydone4j.key-prefix` per app/environment on shared Redis.
- Keep Redis persistent and highly available if queued work must survive a
  restart. Do not treat this demo's in-memory logs as durable audit storage.

## Testing

There are **no automated tests** in this repository: `src/test` does not exist.

```
$ mvn test
[INFO] Compiling 3 source files with javac [debug parameters release 21] to target\classes
[INFO] No sources to compile
[INFO] BUILD SUCCESS
[INFO] Total time:  2.699 s
```

The build succeeds but Surefire runs **0 tests**. Verification of runtime
behavior is manual (startup smoke test + the curl examples above) and requires
a live Redis. See [Groundedness & Verification](#groundedness--verification).

## Deployment

Short version: build the jar or the image, point it at a reachable Redis, and
set `simplydone4j.key-prefix` per environment. The `Dockerfile` is a
multi-stage build (`maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre`);
`docker-compose.yml` wires the demo to a Redis container. There is no CI/CD,
no registry publishing, and no Kubernetes manifest in this repo. See
[`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md).

## Limitations & Known Issues

**This is a demo/sample application and is not production-hardened.**

- **Demo-grade only.** No authentication, authorization, TLS, rate limiting, or
  input hardening. `POST /jobs` is open and unauthenticated.
- **Redis is mandatory.** The starter has no in-memory fallback; without Redis
  the app cannot start or run jobs.
- **No automated tests.** No unit, integration, or contract tests exist
  (`src/test` absent). Runtime behavior is unverified by CI.
- **No CI/CD.** There is no `.github/` workflow; nothing is built or checked
  automatically.
- **Library-version coupling.** Pinned to starter `2.1.0` in `pom.xml`. API
  surface (`JobSubmissionService`, DTOs, `JobStatus`) is whatever 2.1.0
  provides; upgrading requires manual review.
- **Redundant vendored artifact.** `vendor/` contains starter 2.1.0 and the
  `Dockerfile` seeds it into the image's local Maven repository. Now that 2.1.0
  **is** published to Maven Central, this is no longer required and can be
  deleted (see Roadmap).
- **Smoke test exits the process.** `Demo` calls `System.exit(1)` on failure,
  and the "slow" handler sleeps 30 s; this is intentional demo behavior, not a
  long-running service pattern.
- **Single-instance assumptions.** Idempotency, key prefixing, and health
  thresholds are demonstrated with defaults; no tuning for multi-node,
  high-throughput, or multi-tenant use is provided.
- **No data durability beyond Redis.** No snapshots/backups, no migration
  story, no retention policy are configured here.
- **Actuator surface.** `health` and `metrics` are exposed; `show-details:
  always` leaks operational detail, which is fine for a demo but not for a
  public production surface.

## Groundedness & Verification

### Verification status

- **Verified (real numbers).** `mvn test` on 2026-10-16 with Maven 3.9.6 /
  JDK 21.0.7: `BUILD SUCCESS`, 3 source files compiled, **0 tests run**
  (no test sources). The packaged artifact target is
  `target/simplydone4j-demo-1.0.0.jar`.
- **Verified by inspection (not execution).** Library coordinates and version
  (`io.github.learnerview:simplydone4j-spring-boot-starter:2.1.0`), the 12
  meter names, the `simplyDoneHealth` fields, the health thresholds, and the
  `0`-disables behavior were confirmed by decompiling the starter jar in
  `vendor/`. Maven Central publication of 2.1.0 was confirmed by fetching
  `repo1.maven.org` metadata (`<release>2.1.0</release>`, last updated
  2026-10-03).
- **NOT verified.** End-to-end runtime behavior (handler execution, retries,
  DLQ transitions, delayed execution, timeout watchdog, graceful shutdown) was
  **not** executed as part of this documentation pass, because doing so requires
  a live Redis instance. The Actuator JSON shapes and metric values are
  described from the library, not captured from a running system.

### Data & evidence provenance

- **Demo data only.** The startup job payload is hardcoded in `Demo.java`
  (`{"name":"SimplyDone4J"}`, `priority=HIGH`, key `demo-1`). The HTTP API
  builds its payload from query parameters. There are no external data sources,
  no fixtures, and no seed datasets.
- **Real vs mock.** There are **no mocks** — the app always talks to a real
  Redis. `mode=fail` and `mode=slow` are deliberately simulated failure modes
  inside the `greet` handler, not external dependencies.
- **No secrets or credentials** are hardcoded; Redis is addressed via
  `REDIS_URL` (default `redis://localhost:6379`).

### AI-output grounding

There is **no AI or ML in this project**. No model inference, no LLM calls, no
generated content, and no AI-derived data are used at build time or runtime.
Every behavior described here is deterministic application/library code. No
claim in this documentation is derived from AI output.

## Roadmap

- Remove `vendor/` and the `Dockerfile` `COPY vendor/...` line now that starter
  2.1.0 is on Maven Central.
- Add integration tests (Testcontainers Redis) and a CI workflow.
- Add authentication/authorization and TLS for the HTTP API.
- Add Actuator health/liveness probes for the container and tighter
  `show-details` configuration.

## Contributing

This is a demo repository. For changes to job semantics, retries, or the
starter API, contribute upstream to
[`learnerview/simplydone4j`](https://github.com/learnerview/simplydone4j).
For this repo, open an issue or PR at
`https://github.com/learnerview/simplydone4j-demo`. Keep the app a *consumer*
of the library; do not reimplement engine behavior here. No build/lint/test
gate exists yet, so run `mvn clean package` before proposing a change.

## Security

See [`SECURITY.md`](SECURITY.md). In short: this is an unauthenticated demo
that must not be exposed to untrusted networks. Report vulnerabilities via the
process in `SECURITY.md`.

## License

This demo repository contains **no `LICENSE` file**, and its `pom.xml` declares
no `<licenses>` section, so its license is **not specified**.

The library it consumes,
`io.github.learnerview:simplydone4j-spring-boot-starter:2.1.0`, is licensed
under the **MIT License** (per the upstream POM), and the vendored copy under
`vendor/` carries that same upstream metadata.
