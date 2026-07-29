# SimplyDone4J Demo

Demo Spring Boot application for the [SimplyDone4J](https://github.com/learnerview/simplydone4j) background job scheduling engine.

## Prerequisites

- Java 21+
- Redis (start with `docker run -d --name redis -p 6379:6379 redis:7-alpine`)

## Build & Run

```bash
# Build the library first (from project root)
cd SimplyDone4J
mvn clean install -DskipTests

# Build and run the demo
cd simplydone4j-demo
mvn clean package
java -jar target/simplydone4j-demo-1.0.0.jar --spring.profiles.active=worker
```

The `worker` profile enables the scheduler and maintenance services. The app starts on port 8080.

## Auto-submitted jobs

On startup, `DemoRunner` submits ~15 jobs to demonstrate:

| Job type | Count | Behavior |
|---|---|---|
| `quick-success` | ~8 | Completes immediately, logs payload |
| `failing-task` | 2 | Throws `RuntimeException` — triggers retry, then DLQ |
| `callback-test` | 1 | Completes with a callback URL |
| `long-running` | 1 | Runs for 10s with a 3s timeout (forced fail) |
| Delayed | 1 | Scheduled 30s in the future |
| Idempotency | 1 | Same key resubmitted — returns existing job |
| Rate limit burst | 5 | Tests per-producer sliding window rate limiter |

## REST API

All endpoints are under `http://localhost:8080/api/jobs`.

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/jobs` | Submit a new job |
| `GET` | `/api/jobs/{jobId}` | Get job details |
| `POST` | `/api/jobs/{jobId}/cancel` | Cancel a queued job |
| `GET` | `/api/jobs/stats` | Queue statistics (sizes, rates) |
| `GET` | `/api/jobs/stats/status` | Counts by status |
| `GET` | `/api/jobs/stats/queues` | Queue depths by priority |

### Submit a job

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{"jobType":"quick-success","idempotencyKey":"my-key-1","priority":"HIGH","payload":{"task":"test"}}'
```

### View stats

```bash
curl http://localhost:8080/api/jobs/stats
```

## Demo handlers

Defined in `DemoHandlers.java`:

- **`quick-success`** — logs job info and completes
- **`failing-task`** — always throws an exception (retry + DLQ demo)
- **`long-running`** — sleeps 10s (timeout demo with 3s limit)
- **`callback-test`** — completes and fires the configured callback URL

## Job events

`DemoEventListener` logs every job lifecycle event (CREATE, START, COMPLETE, FAIL, RETRY, CANCEL) to the console.

## Configuration

See `src/main/resources/application.yml` for all settings.