# SimplyDone4J Demo

Demo Spring Boot application for [SimplyDone4J](https://github.com/learnerview/simplydone4j) — the embeddable, Redis-backed job scheduling engine.

## Prerequisites

- Java 21+ and Maven 3.8+
- Redis (start with `docker run -d --name redis -p 6379:6379 redis:7-alpine`)

## Build & Run

```bash
# Build the library first (required dependency)
cd SimplyDone4J
mvn clean install -DskipTests

# Build and run the demo (sibling directory)
cd ../simplydone4j-demo
mvn clean package
java -jar target/simplydone4j-demo-1.0.0.jar --spring.profiles.active=worker
```

The `worker` profile enables scheduling and maintenance. App starts on port 8080.

## What it demonstrates

### Auto-submitted jobs (~15 on startup)

| Job type | Count | Behavior |
|---|---|---|
| `quick-success` | ~8 | Completes immediately, logs payload |
| `failing-task` | 2 | Throws `RuntimeException` — triggers retry, then DLQ |
| `callback-test` | 1 | Completes with a callback URL |
| `long-running` | 1 | Runs for 10s with a 3s timeout (forced failure) |
| Delayed | 1 | Scheduled 30s in the future |
| Idempotency | 1 | Same key resubmitted — returns existing job |
| Rate limit burst | 5 | Tests per-producer sliding window rate limiter |

### REST API

Base URL: `http://localhost:8080/api/jobs`

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/jobs` | Submit a new job |
| `GET` | `/api/jobs/{jobId}` | Get job details |
| `POST` | `/api/jobs/{jobId}/cancel` | Cancel a queued job |
| `GET` | `/api/jobs/stats` | Queue statistics (sizes, rates) |
| `GET` | `/api/jobs/stats/status` | Counts by status |
| `GET` | `/api/jobs/stats/queues` | Queue depths by priority |

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{"jobType":"quick-success","idempotencyKey":"my-key-1","priority":"HIGH","payload":{"task":"test"}}'

curl http://localhost:8080/api/jobs/stats
```

### Demo handlers

Defined in `DemoHandlers.java`:

- **`quick-success`** — logs job info and completes
- **`failing-task`** — always throws (retry + DLQ demo)
- **`long-running`** — sleeps 10s (timeout demo with 3s limit)
- **`callback-test`** — completes and fires the configured callback URL

### Job events

`DemoEventListener` logs every lifecycle event (CREATE, START, COMPLETE, FAIL, RETRY, CANCEL).

## Configuration

See `src/main/resources/application.yml` for all settings.
