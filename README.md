# SimplyDone4J Demo

Minimal Spring Boot app that demonstrates [SimplyDone4J](https://github.com/learnerview/simplydone4j)
with Redis: register a handler, submit jobs, observe retries and delayed execution,
and poll job status through a small HTTP API.

## Run with Docker (recommended)

```bash
docker compose up --build
```

This starts Redis and the demo, submits one job on startup, and logs the result.
Look for `VERIFIED job ... completed with result: hello {"name":"SimplyDone4J"}`.
To run it beside another service that already uses port 8080:

```bash
DEMO_PORT=8082 docker compose up --build
```

## Run locally

```bash
# 1. the library must be in your local repo
cd ../SimplyDone4J && mvn clean install -DskipTests

# 2. Redis
docker run -d --name redis -p 6379:6379 redis:7-alpine

# 3. the demo
cd ../simplydone4j-demo && mvn clean package
java -jar target/simplydone4j-demo-1.0.0.jar
```

Redis is required; the starter has no in-memory fallback. The app exits non-zero
if the startup smoke-test job does not reach a terminal state, so it doubles as a
smoke test.

## What it checks

`src/main/java/com/demo/Demo.java` is the startup smoke test:

1. Registers a `greet` handler that logs and returns `"hello " + payload`.
2. Submits one job (`priority=HIGH`, idempotency key `demo-1`).
3. Polls until a terminal status (`SUCCESS`, `FAILED`, `DLQ`, `CANCELLED`),
   then asserts it succeeded — `System.exit(1)` otherwise.

Status values come from the library's `JobStatus` enum: `QUEUED`, `RUNNING`,
`RETRY_SCHEDULED`, `SUCCESS`, `FAILED`, `CANCELLED`, `DLQ`.

## HTTP examples

The controller exposes the same submission service used by application code.
The response contains the job ID; clients should persist it and poll the job
endpoint rather than blocking the request thread.

```bash
# Normal job with an explicit idempotency key
curl -X POST "http://localhost:8080/jobs?name=Ada&priority=HIGH&key=order-123"

# Submit the same request again: the stable key returns the existing job
curl -X POST "http://localhost:8080/jobs?name=Ada&priority=HIGH&key=order-123"

# Handler failure: retries, then reaches FAILED/DLQ according to configuration
curl -X POST "http://localhost:8080/jobs?name=Bad&mode=fail&key=failure-1"

# Delayed execution and a handler timeout
curl -X POST "http://localhost:8080/jobs?name=Later&delaySeconds=5&key=delayed-1"
curl -X POST "http://localhost:8080/jobs?name=Slow&mode=slow&timeoutSeconds=1&key=timeout-1"

# Poll a returned job ID
curl "http://localhost:8080/jobs/<jobId>"
```

## Usage guidelines

- Use a stable idempotency key derived from the business operation, such as
  `order-123-email`, not a random UUID generated on every retry.
- Keep handlers idempotent: a retry can happen after a worker has performed an
  external side effect but before the result is recorded.
- Submit work quickly and return the job ID; poll or consume lifecycle events
  instead of waiting in an HTTP request.
- Set `timeoutSeconds` for work with a known upper bound and make the handler
  honor interruption when it performs blocking I/O.
- Treat `FAILED` and `DLQ` as distinct operational outcomes and alert on DLQ
  growth rather than silently retrying forever.
- Use a distinct `simplydone4j.key-prefix` for each application or environment
  when Redis is shared.
- Keep Redis persistent and highly available when queued work must survive a
  process restart.
- Expose health and metrics to the application monitoring system; do not use
  the demo's in-memory logs as durable audit storage.

## Verify the 2.1.0 operational features

While it runs, the actuator endpoints expose the starter's instrumentation:

```bash
# health contributor contributed by the starter
curl -s http://localhost:8080/actuator/health | jq '.components.simplyDone'

# 14 simplydone4j metrics
curl -s http://localhost:8080/actuator/metrics | jq '.names[] | select(startswith("simplydone4j"))'

# per-outcome counters
curl -s "http://localhost:8080/actuator/metrics/simplydone4j.jobs.completed?tag=outcome:SUCCESS"
```

Health reports `queueDepths` per priority, `totalQueued`, `running` and
`deadLettered`, and flips to `DOWN` at
`simplydone4j.health.dead-letter-threshold` (default 1) or
`max-queue-depth` (default 100000). Set either to `0` to disable that check.
The demo enables the scheduler and monitoring sections explicitly so the
configuration is visible rather than relying on starter defaults.

## A note on `vendor/`

Starter 2.1.0 is not on Maven Central yet, so its jar and pom are checked into
`vendor/` and seeded into the image's local repository by the Dockerfile.
**Once 2.1.0 is published, delete `vendor/` and the `COPY vendor/...` line.**

## Configuration

See `src/main/resources/application.yml`. Redis is read from `REDIS_URL`
(default `redis://localhost:6379`).
