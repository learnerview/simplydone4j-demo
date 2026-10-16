# Security Policy

## Scope and threat model

`simplydone4j-demo` is a **demo/sample application**, not a production service.
It has **no authentication, authorization, TLS, rate limiting, or input
sanitization**. Its HTTP API (`POST /jobs`, `GET /jobs/{jobId}`) is open, and
`management.endpoint.health.show-details: always` exposes operational detail.

**Do not expose this application to untrusted networks.** It is intended to run
on a developer machine or an isolated network with a disposable Redis instance.

## Supported versions

This is a demo; only the `main` branch is maintained. There are no supported
release lines and no security back-ports.

## Reporting a vulnerability

- For issues in the **starter/library**, report to the upstream project:
  https://github.com/learnerview/simplydone4j (see its own `SECURITY.md`).
- For issues in **this demo** (the `com.demo` source, Dockerfile, or Compose
  file), open a private report or issue at:
  https://github.com/learnerview/simplydone4j-demo

Please include: a description, reproduction steps, affected file(s)/version
commit, and the potential impact. Do not include secrets.

## Secret handling

- **No secrets are stored in this repository.** There are no credentials,
  tokens, API keys, or private keys checked in.
- Redis is configured through the `REDIS_URL` environment variable
  (default `redis://localhost:6379`). If you point it at an authenticated or
  managed Redis, **supply credentials via the environment or a secret manager —
  never commit them**. Note the URL may carry a password
  (`redis://:password@host:6379`); treat that value as a secret.
- The Docker Compose file sets `REDIS_URL=redis://redis:6379` for the local
  Compose network only and contains no secrets.
- `vendor/` contains the upstream starter jar/pom; no secrets are embedded.

## Dependency and supply-chain notes

- The application depends on
  `io.github.learnerview:simplydone4j-spring-boot-starter:2.1.0`, resolved from
  Maven Central.
- A copy of that artifact is also vendored under `vendor/` and seeded into the
  image's local Maven repository by the `Dockerfile`. This is now redundant
  (2.1.0 is on Central) and is a candidate for removal. Verify the vendored
  artifact's checksum against Maven Central if you keep it.
- No dependency-vulnerability scanning (e.g. OWASP Dependency-Check, Dependabot)
  or SBOM generation is configured.
