# SimplyDone4J Demo

Minimal Spring Boot app that proves [SimplyDone4J](https://github.com/learnerview/simplydone4j)
works end to end: register a handler, submit one job, watch a worker thread run it.

## Run with Docker (recommended)

```bash
docker compose up --build
```

This starts Redis and the demo, submits one job on startup, and logs the result.
Look for `VERIFIED job ... completed with result: hello {"name":"SimplyDone4J"}`.

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
if the job does not reach a terminal state, so it doubles as a smoke test.

## What it checks

`src/main/java/com/demo/Demo.java` is the whole test:

1. Registers a `greet` handler that logs and returns `"hello " + payload`.
2. Submits one job (`priority=HIGH`, idempotency key `demo-1`).
3. Polls until a terminal status (`SUCCESS`, `FAILED`, `DLQ`, `CANCELLED`),
   then asserts it succeeded — `System.exit(1)` otherwise.

Status values come from the library's `JobStatus` enum: `QUEUED`, `RUNNING`,
`RETRY_SCHEDULED`, `SUCCESS`, `FAILED`, `CANCELLED`, `DLQ`.

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

## A note on `vendor/`

Starter 2.1.0 is not on Maven Central yet, so its jar and pom are checked into
`vendor/` and seeded into the image's local repository by the Dockerfile.
**Once 2.1.0 is published, delete `vendor/` and the `COPY vendor/...` line.**

## Configuration

See `src/main/resources/application.yml`. Redis is read from `REDIS_URL`
(default `redis://localhost:6379`).
