# Deployment

This is a **demo**, so "deployment" here means running the sample locally or in
a container. There is no production deployment pipeline, no orchestrator
manifests, and no CI/CD in this repository.

## Build artifacts

- **Jar** — `mvn clean package` produces
  `target/simplydone4j-demo-1.0.0.jar` (Spring Boot executable jar, built by
  `spring-boot-maven-plugin`).
- **Image** — the multi-stage `Dockerfile` produces a runtime image on
  `eclipse-temurin:21-jre`.

## Dockerfile

[`Dockerfile`](../Dockerfile) is a two-stage build:

1. **build stage** — `maven:3.9-eclipse-temurin-21`. Copies `pom.xml`, seeds
   the vendored starter into `/root/.m2/repository/io/github/learnerview`,
   copies `src`, and runs `mvn -B -q clean package -DskipTests`.
2. **runtime stage** — `eclipse-temurin:21-jre`. Copies the jar to `app.jar`,
   `EXPOSE 8080`, and runs `java -jar /app/app.jar`.

Notes:
- The build uses `-DskipTests` (and there are no tests anyway).
- The `COPY vendor/...` line is **redundant** now that starter 2.1.0 is on
  Maven Central. The build would still resolve 2.1.0 without it. Removing
  `vendor/` and that line is a pending cleanup (see Roadmap in the README).

## Docker Compose

[`docker-compose.yml`](../docker-compose.yml) defines two services:

- `redis` — `redis:7-alpine` with a `redis-cli ping` healthcheck
  (2 s interval, 15 retries).
- `demo` — built from `.`, `depends_on` redis (`condition: service_healthy`),
  `REDIS_URL=redis://redis:6379`, and host port `${DEMO_PORT:-8080}` → `8080`.

```bash
docker compose up --build            # default host port 8080
DEMO_PORT=8082 docker compose up --build
```

## Local (non-container)

```bash
docker run -d --name redis -p 6379:6379 redis:7-alpine
mvn clean package
java -jar target/simplydone4j-demo-1.0.0.jar
```

The app exits non-zero if its startup smoke-test job does not reach `SUCCESS`,
so it can be used as a one-shot readiness check when Redis is present.

## Runtime expectations and caveats

- **Redis is a hard dependency.** No in-memory fallback exists.
- **Single instance / single Redis.** No clustering, replication, HA, or
  worker-scaling configuration is provided.
- **Ports.** App listens on `8080`; Redis on `6379`. Both are unauthenticated.
- **Graceful shutdown.** `server.shutdown=graceful` with a 30 s
  `timeout-per-shutdown-phase` window.
- **No probes.** The Compose `demo` service has no healthcheck or
  liveness/readiness probe; only Redis is health-checked.
- **No secrets management, TLS, or reverse proxy.**
- **No registry push / CI.** Images are built locally only.

For production concerns and gaps, see
[`PRODUCTION_READINESS.md`](PRODUCTION_READINESS.md).
