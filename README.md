# RoundTable (桌面二维码点单系统)

> **RoundTable** — a concurrency-safe QR dine-in ordering backend. The name is a
> double entendre: a table's party shares one *round table* session, and orders
> arrive in *rounds* (加菜) settled by a single bill.

API-only Java backend for concurrency-safe dine-in QR-code ordering. A party scans
a table QR code, joins a shared session, orders dishes in **multiple rounds (加菜)**,
and settles **one bill** at the end. Kitchen/manager staff work orders through a
separate authenticated API. Payment is simulated but keeps a real-provider seam.

Engineering focus: shared-session coordination, idempotent multi-round ordering,
inventory correctness, staff RBAC, reliable messaging, repeatable tests, and an
observable two-instance deployment.

## Status

Foundation milestone complete:

- Java 21 + Spring Boot 3.5
- MySQL schema via Flyway migrations
- uniform Problem Details errors + `X-Trace-Id`
- OpenAPI (`/swagger-ui.html`) + Actuator health
- real-MySQL integration tests with Testcontainers
- two app instances behind Nginx via Docker Compose
- GitHub Actions CI

Business modules (auth, tables/sessions, menu/inventory, cart, multi-round orders,
bill/checkout, kitchen) are added as independently reviewed vertical slices.

## Prerequisites

- Docker Desktop
- Java 21 (to build the jar / run tests outside Docker)

## Run locally

```bash
cp .env.example .env
./mvnw -DskipTests package
DOCKER_BUILDKIT=0 docker build -t roundtable:local .
docker compose up -d
./scripts/smoke-test.sh
```

- API docs: <http://localhost:8080/swagger-ui.html>
- Readiness: <http://localhost:8080/actuator/health/readiness>

> The Dockerfile packages a jar you build first (`./mvnw package`). `DOCKER_BUILDKIT=0`
> is required only because this project's path contains non-ASCII characters, which
> the default Docker BuildKit builder rejects; on an ASCII-only path you can omit it.

Stop (database data is preserved):

```bash
docker compose down
```

## Test

```bash
./mvnw verify
```

Integration tests start a real MySQL 8.4 container, so Docker must be running.

## Design

- [Base backend design](docs/superpowers/specs/2026-07-13-restaurant-ordering-backend-design.md)
- [Multi-round dine-in addendum](docs/superpowers/specs/2026-07-28-dine-in-multi-round-addendum.md) — the authoritative ordering model
- [Foundation implementation plan](docs/superpowers/plans/2026-07-13-foundation-implementation-plan.md)
- [Devlog](docs/devlog/)
