# Configuration

The application is configured via `src/main/resources/application.yml`, the
`docker-compose.yml` environment, and (for library behavior) the SimplyDone4J
starter's own defaults. This document records every value that is actually set
in this repository, plus the relevant library defaults.

## Keys defined in this repository

Source: [`src/main/resources/application.yml`](../src/main/resources/application.yml).

| Key | Purpose | Default | Required |
|-----|---------|---------|----------|
| `REDIS_URL` (env → `spring.data.redis.url`) | Redis connection URL | `redis://localhost:6379` | Yes — Redis must be reachable |
| `spring.lifecycle.timeout-per-shutdown-phase` | Graceful-shutdown window | `30s` | No |
| `simplydone4j.key-prefix` | Redis key namespace | `demo` | No |
| `simplydone4j.scheduler.enabled` | Enable delayed/retry scheduler | `true` | No |
| `simplydone4j.monitoring.enabled` | Enable health + metrics instrumentation | `true` | No |
| `simplydone4j.retry.max-attempts` | Max handler attempts before terminal failure | `3` | No |
| `simplydone4j.retry.initial-delay-seconds` | Base retry backoff delay | `1` | No |
| `management.endpoints.web.exposure.include` | Exposed Actuator endpoints | `health,metrics` | No |
| `management.endpoint.health.show-details` | Health detail verbosity | `always` | No |
| `server.port` | HTTP listen port | `8080` | No |
| `server.shutdown` | Shutdown mode | `graceful` | No |

### docker-compose.yml

| Key | Purpose | Default |
|-----|---------|---------|
| `DEMO_PORT` (env) | Host port mapped to container `8080` | `8080` |
| `REDIS_URL` (env, set by Compose) | Redis URL inside the Compose network | `redis://redis:6379` |

## Library defaults (not set here)

These come from starter 2.1.0 and were confirmed by decompiling the jar in
`vendor/`. They are **not** present in this repo's `application.yml`, so the
library defaults apply.

| Key | Purpose | Library default |
|-----|---------|-----------------|
| `simplydone4j.health.enabled` | Master switch for the health contributor | `true` |
| `simplydone4j.health.dead-letter-threshold` | DLQ count that flips health to `DOWN`; `0` disables the check | `1` |
| `simplydone4j.health.max-queue-depth` | Total queued count that flips health to `DOWN`; `0` disables the check | `100000` |
| `simplydone4j.metrics.queue-depth-refresh-seconds` | Queue-depth gauge refresh interval | see library docs |
| `simplydone4j.redis.cluster-mode` / `sentinel-master` / `sentinel-nodes` | Advanced Redis topology | library defaults |

The starter exposes additional groups (`queue`, `worker`, `executor`,
`rate-limit`, `retention`, `uniqueness`, `webhook`, `weights`, `redis`) that
this demo does not configure; refer to the upstream starter documentation.

## Health behavior (verified from 2.1.0 bytecode)

`simplyDoneHealthIndicator` computes `totalQueued` across priorities and counts
of `DLQ` and `RUNNING` jobs, then:

- reports `DOWN` if `deadLetterThreshold > 0` **and**
  `deadLettered >= deadLetterThreshold`;
- reports `DOWN` if `maxQueueDepth > 0` **and** `totalQueued > maxQueueDepth`;
- otherwise reports `UP`.

Setting either threshold to `0` disables only that check.

## Overriding configuration

- **Local:** edit `application.yml` or pass Spring Boot properties, e.g.
  `java -jar target/simplydone4j-demo-1.0.0.jar --simplydone4j.key-prefix=myapp`.
- **Environment:** `REDIS_URL` is the intended runtime override (it is the only
  value wired to a variable in `application.yml`).
- **Compose:** set `REDIS_URL`/`DEMO_PORT` in the shell or a `.env` file.

## Secrets

No secrets are stored in the repository. If `REDIS_URL` carries a password
(`redis://:password@host:6379`), supply it via the environment or a secret
manager and keep it out of version control. See [`../SECURITY.md`](../SECURITY.md).
