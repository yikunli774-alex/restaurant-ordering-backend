# Restaurant Ordering Backend Design

**Status:** Proposed for review  
**Date:** 2026-07-13  
**Audience:** Java backend internship project  
**Delivery:** Backend REST API only; no customer or staff frontend

## 1. Executive summary

This project is a modular-monolith restaurant ordering backend focused on
concurrency correctness and production-style engineering. Multiple anonymous
participants can join one table session and edit a shared cart. The first valid
order submission atomically claims the session, reserves stock, creates exactly
one order, and starts a simulated payment flow. Successful payment closes the
ordering session and makes the order visible to authenticated kitchen staff.

The project deliberately optimizes for an honest, defensible internship
portfolio rather than artificial business scale. A twenty-table restaurant does
not need microservices or sharding, but it still needs correct handling of
concurrent cart updates, double submission, inventory races, duplicate payment
callbacks, message redelivery, employee authorization, and process crashes.

## 2. Goals

1. Provide a complete API-only ordering flow that can be exercised through
   OpenAPI, REST Assured, and load-test scripts.
2. Demonstrate concurrency correctness with executable invariants, not only
   architecture claims.
3. Separate anonymous customer capabilities from authenticated staff RBAC.
4. Run two application instances to prove that correctness does not depend on
   JVM-local state or locks.
5. Use MySQL as the durable source of truth, Redis for ephemeral shared state
   and fast coordination, and RocketMQ for asynchronous delivery.
6. Provide repeatable local deployment with Docker Compose and repeatable tests
   with Testcontainers.
7. Expose health, metrics, traces, and structured logs for diagnosis.
8. Keep module boundaries explicit enough to support a future service split
   without paying the microservice cost now.

## 3. Non-goals

- Customer registration, password login, social login, or profiles
- Customer or staff web/mobile interfaces
- Real payment-provider integration or real money movement
- Coupons, membership, points, promotions, delivery, or refunds
- Multiple settlement modes; version one is one-order-one-payment
- Multiple order batches inside one table session
- Multi-tenant store administration; `store_id` is retained for evolution only
- Spring Cloud, Nacos, Seata, Kubernetes, service mesh, or distributed database
- Elasticsearch, recommendation systems, analytics, or AI features
- Claims of production traffic or invented TPS numbers

## 4. Business rules

### 4.1 Table session

- A dining table has a stable, signed table code.
- A table may have at most one `OPEN` or `PENDING_PAYMENT` ordering session.
- The first join request creates the active session; later joins enter it.
- Joining returns an opaque participant token scoped to that session.
- All active participants may read and mutate the shared cart.
- All active participants may submit the order.
- The first valid submission wins. Other submissions must not create another
  order or reserve stock again.
- A successful payment closes the table session.
- A kitchen employee with `table_session:close` may force-close an `OPEN` or
  `PENDING_PAYMENT` session with a reason.
- Closing an `OPEN` session discards its ephemeral cart.
- Force-closing a `PENDING_PAYMENT` session cancels its unpaid order and releases
  reserved stock exactly once.
- Closing an already closed session is idempotent.

The session represents an ordering attempt, not permanent physical occupancy.
After it closes, scanning the same table code may create a new session.

### 4.2 One-order-one-payment

- Each table session can create at most one order.
- Order submission freezes the shared cart and prevents further mutations.
- The created order is initially `PENDING_PAYMENT`.
- Payment may be retried for the same order until success or expiry.
- Successful payment moves the order to `CONFIRMED`, closes the table session,
  and emits an `OrderPaid` event for the kitchen.
- A delayed expiry command cancels an unpaid order and releases its inventory.
- Duplicate or late callbacks never repeat state changes.

### 4.3 Kitchen workflow

The fulfillment state machine is:

```text
PENDING_PAYMENT -> CONFIRMED -> PREPARING -> READY -> COMPLETED
       |               |
       +-> CANCELLED <-+
```

- Only `CONFIRMED` orders are visible in the default kitchen queue.
- Kitchen staff may accept/start, mark ready, and complete an order.
- State changes use conditional updates, so repeated or competing commands are
  safe and invalid transitions return a conflict.

## 5. Architecture

### 5.1 Runtime topology

```text
OpenAPI / REST Assured / load tests
                 |
              Nginx
            /       \
      ordering-1   ordering-2
            \       /
      MySQL + Redis + RocketMQ
                 |
   Prometheus + Grafana + Jaeger
```

The application is one deployable Spring Boot artifact. Running two instances
demonstrates distributed behavior without introducing service-to-service calls.

### 5.2 Technology baseline

- Java 21
- Spring Boot 3.5.x
- Maven Wrapper
- Spring MVC and Bean Validation
- Spring Security and method authorization
- MyBatis-Plus for ordinary CRUD
- Explicit MyBatis SQL for locking, CAS, inventory, and idempotency paths
- MySQL 8
- Redis with Lua scripts
- Apache RocketMQ
- Flyway database migrations
- SpringDoc OpenAPI
- JUnit 5, AssertJ, REST Assured, Awaitility, Testcontainers
- ArchUnit for module-boundary tests
- Actuator, Micrometer, Prometheus, OpenTelemetry, Jaeger
- Docker, Docker Compose, Nginx, GitHub Actions

Spring Boot 3.5 is selected over Boot 4 for broad library compatibility and
recognizability in current Java internship interviews. Java 21 is an LTS release
with modern language and JVM features without making the project a migration
exercise.

### 5.3 Code organization

Use package-by-feature. Each feature owns its API, application use cases, domain
logic, and persistence adapter.

```text
com.example.ordering
├── staff
│   ├── api
│   ├── application
│   ├── domain
│   └── infrastructure
├── table
├── participant
├── menu
├── cart
├── order
├── inventory
├── payment
├── kitchen
├── outbox
└── common
```

Rules enforced by ArchUnit:

- `api` may call `application`, never repositories directly.
- `application` defines transaction boundaries and orchestrates domain objects.
- `domain` does not depend on Spring MVC, MyBatis, Redis, or RocketMQ.
- `infrastructure` implements ports defined by its owning feature.
- Cross-feature access uses an application interface or a domain event, not a
  foreign mapper.
- `common` contains only genuinely shared primitives and must not become a dump.

## 6. Domain and persistence model

### 6.1 Durable MySQL data

| Table | Purpose | Important constraints |
|---|---|---|
| `store` | Store identity retained for evolution | unique `code` |
| `dining_table` | Stable restaurant table | unique `(store_id, code)` |
| `table_session` | One ordering attempt | version column; generated active marker |
| `participant` | Anonymous session member | unique token hash; session FK |
| `staff_account` | Kitchen/manager login | unique username; password hash; status |
| `role` | RBAC role | unique code |
| `permission` | Atomic authority | unique code |
| `staff_role` | Staff-to-role mapping | unique pair |
| `role_permission` | Role-to-permission mapping | unique pair |
| `menu_item` | Sellable SKU and current price | store FK; status; version |
| `inventory` | Available and reserved counts | unique menu item; non-negative checks |
| `orders` | One immutable-priced order | unique session ID; unique order number |
| `order_item` | Price/name snapshot | order FK; positive quantity |
| `payment` | Simulated payment attempt/result | unique provider request/reference |
| `api_idempotency` | Durable request result | unique `(scope, idempotency_key)` |
| `inventory_ledger` | Auditable reserve/release movements | unique business operation ID |
| `outbox_event` | Reliable event publication | event status and retry schedule |
| `audit_log` | Staff security and sensitive actions | append-only application behavior |

`table_session` uses a stored generated column:

```sql
active_table_id BIGINT GENERATED ALWAYS AS (
  CASE
    WHEN status IN ('OPEN', 'PENDING_PAYMENT') THEN dining_table_id
    ELSE NULL
  END
) STORED,
UNIQUE KEY uk_table_session_active (active_table_id)
```

MySQL permits multiple `NULL` values in a unique index, so historical closed
sessions remain valid while at most one active session can exist per table. A
concurrent join integration test verifies this database invariant.

Money is stored as `DECIMAL`, never floating point. Order items snapshot name
and unit price at submission so later menu changes cannot alter an existing bill.

### 6.2 Ephemeral Redis data

```text
cart:{sessionId}                 Redis Hash: menuItemId -> quantity
cart:meta:{sessionId}            version, state, frozenBy, frozenAt
cart:op:{sessionId}:{operationId} cached operation result with TTL
participant:token:{tokenHash}    participant/session lookup with TTL
staff:session:{sessionId}         staff ID, token version, authorities, TTL
staff:sessions:{staffId}          active session IDs for immediate revocation
idempotency:fast:{scope}:{key}    fast result lookup; MySQL remains authoritative
```

Redis persistence is enabled for the demo environment, but the design still
handles process crashes and expired keys explicitly. Redis is not treated as the
durable source for orders, inventory, payments, authorization definitions, or
idempotency results.

## 7. Authentication and authorization

### 7.1 Anonymous participants

- `POST /api/v1/table-sessions/join` accepts a signed table code.
- The response contains `sessionId`, `participantId`, and a random opaque token.
- Only the token hash is stored.
- Customer APIs require `X-Participant-Token`.
- The participant must belong to the target session and the session must permit
  the requested operation.
- Tokens expire when the session closes and have an absolute TTL as cleanup.

### 7.2 Staff login

- Staff accounts are provisioned by a manager; there is no public registration.
- Passwords use Spring Security's adaptive password encoder.
- Login checks password, account status, failed-attempt count, and temporary lock.
- A signed access token is valid for 15 minutes and contains only staff ID,
  server session ID, token version, issued time, and expiry. It does not carry
  authoritative permissions.
- Login also returns a random opaque refresh token valid for seven days. Only its
  hash is stored in the Redis staff session, and every refresh rotates it.
- Redis stores the live staff session and current authorities.
- Logout, disable, role change, and password change revoke active sessions.
- A missing Redis staff session is treated as revoked. The API fails closed and
  requires login again rather than silently reconstructing deleted authority.
- If Redis itself is unavailable, staff authentication returns dependency
  unavailable instead of bypassing server-side revocation checks.

Initial roles:

```text
KITCHEN: order:read, order:prepare, order:ready, order:complete,
         table_session:close
MANAGER: all KITCHEN permissions plus staff:manage, menu:manage,
         inventory:manage
```

Customer tokens and staff tokens use separate validation chains and cannot be
used against each other's endpoints.

## 8. API surface

All APIs are versioned under `/api/v1`. Responses use JSON. Errors use RFC-style
Problem Details with stable application error codes and a trace ID.

### 8.1 Customer APIs

```text
POST   /api/v1/table-sessions/join
GET    /api/v1/table-sessions/{sessionId}
GET    /api/v1/menu-items
GET    /api/v1/table-sessions/{sessionId}/cart
PUT    /api/v1/table-sessions/{sessionId}/cart/items/{menuItemId}
DELETE /api/v1/table-sessions/{sessionId}/cart
POST   /api/v1/table-sessions/{sessionId}/orders
GET    /api/v1/orders/{orderId}
POST   /api/v1/orders/{orderId}/payment-attempts
```

Cart mutation body:

```json
{
  "operationId": "01J2...",
  "quantityDelta": 1,
  "expectedVersion": 7
}
```

Order submission requires an `Idempotency-Key` header and `expectedCartVersion`.

### 8.2 Simulated provider API

```text
POST /api/v1/simulator/payments/{paymentId}/succeed
POST /api/v1/internal/payment-callbacks
```

The simulator creates an HMAC-signed callback and invokes the same internal path
used by a real provider adapter. Tests can deliver the same callback repeatedly
or concurrently. The internal callback endpoint is not authorized by staff JWT;
it validates timestamp, signature, replay window, and provider event ID.

### 8.3 Staff APIs

```text
POST /api/v1/staff/auth/login
POST /api/v1/staff/auth/refresh
POST /api/v1/staff/auth/logout

GET  /api/v1/kitchen/orders
POST /api/v1/kitchen/orders/{orderId}/prepare
POST /api/v1/kitchen/orders/{orderId}/ready
POST /api/v1/kitchen/orders/{orderId}/complete
POST /api/v1/kitchen/table-sessions/{sessionId}/close

POST/PATCH /api/v1/management/staff
POST/PATCH /api/v1/management/menu-items
PUT        /api/v1/management/inventory/{menuItemId}
```

Management endpoints are deliberately minimal and exist to support setup,
authorization demonstration, and repeatable test data.

## 9. Concurrency and consistency design

### 9.1 Shared-cart mutation

A Redis Lua script performs one cart mutation atomically:

1. Validate that cart state is `OPEN`.
2. Return the stored result if `operationId` was already processed.
3. Compare `expectedVersion` when supplied.
4. Apply the quantity delta, reject negative quantity, and delete a zero row.
5. Increment cart version.
6. Save the operation result with a TTL.
7. Return the new cart version and quantity.

Lua is used because multiple Redis commands from Java would otherwise interleave.
The script is small, deterministic, and tested directly.

### 9.2 Order submission

Submission must solve two distinct problems:

- The same request is retried: handled by `Idempotency-Key` and a durable result.
- Different participants submit the same session: handled by cart/session CAS and
  the unique `orders.table_session_id` constraint.

Flow:

1. Authenticate participant and calculate a canonical request fingerprint.
2. Check durable idempotency ownership; the Redis entry is only a fast path.
3. Atomically freeze the Redis cart if its state and version match, returning a
   snapshot plus a unique freeze token.
4. In one MySQL transaction:
   - CAS `table_session` from `OPEN` to `PENDING_PAYMENT`;
   - validate every current menu item and load server-side prices;
   - reserve inventory with conditional SQL;
   - insert the order and item snapshots;
   - insert inventory-ledger rows;
   - persist the idempotency result;
   - insert `OrderCreated` and payment-expiry outbox records.
5. After commit, finalize/delete the frozen cart only when its freeze token
   matches.
6. If the MySQL transaction fails, unfreeze only the matching cart snapshot.

The Redis freeze and MySQL transaction cannot be globally atomic. Recovery is
therefore explicit: a scheduled reconciler scans carts frozen past a short
threshold. If the session/order committed, it finalizes the cart; otherwise it
unfreezes it. This is preferable to pretending a distributed lock creates a
cross-system transaction.

### 9.3 Inventory

Inventory reservation uses a single conditional update:

```sql
UPDATE inventory
SET available = available - :quantity,
    reserved = reserved + :quantity,
    version = version + 1
WHERE menu_item_id = :menuItemId
  AND available >= :quantity;
```

An affected-row count of zero means insufficient stock or a concurrent winner.
Each reserve/release has a unique ledger operation ID. A retry therefore cannot
reserve or release twice.

### 9.4 Payment callback

The callback first inserts the unique provider event ID. It then conditionally
transitions the payment and order. Only the winning transition closes the table
session and inserts `OrderPaid`. Duplicate callbacks return success without
repeating side effects, because providers intentionally retry callbacks.

### 9.5 MQ delivery

Business transactions write `outbox_event` rows, never publish directly inside
the transaction. A publisher claims pending rows with a lease, sends them to
RocketMQ, and records publication. If it crashes after send and before recording,
the event may be sent twice; consumers therefore deduplicate by event ID.

RocketMQ delay delivery triggers unpaid-order expiry. The handler re-reads the
order and conditionally cancels only `PENDING_PAYMENT` orders. Payment success
and expiry may race; their state conditions ensure exactly one wins.

## 10. Error handling

Representative stable error codes:

| HTTP | Code | Meaning |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Invalid request shape or value |
| 401 | `PARTICIPANT_TOKEN_INVALID` | Missing/invalid customer credential |
| 401 | `STAFF_SESSION_INVALID` | Missing, expired, or revoked staff session |
| 403 | `PERMISSION_DENIED` | Authenticated staff lacks authority |
| 404 | `RESOURCE_NOT_FOUND` | Resource does not exist in caller scope |
| 409 | `CART_VERSION_CONFLICT` | Cart changed since caller read it |
| 409 | `SESSION_ALREADY_SUBMITTED` | Another request created the session order |
| 409 | `INVALID_STATE_TRANSITION` | Command conflicts with current state |
| 409 | `IDEMPOTENCY_KEY_REUSED` | Same key used with a different payload |
| 409 | `INSUFFICIENT_STOCK` | Conditional reservation failed |
| 429 | `LOGIN_RATE_LIMITED` | Too many login attempts |
| 503 | `DEPENDENCY_UNAVAILABLE` | Required infrastructure is unavailable |

Responses never expose stack traces, SQL, tokens, password hashes, or internal
class names. Logs retain the exception and trace ID for diagnosis.

## 11. Observability

- JSON structured logs in containers
- Correlation/trace ID returned in response headers and Problem Details
- Actuator health groups for liveness and readiness
- Micrometer metrics exported to Prometheus
- OpenTelemetry instrumentation exported through OTLP to Jaeger
- Audit events for login, logout, staff changes, force-close, and inventory edits

Custom metrics include:

```text
cart_mutations_total{result}
order_submissions_total{result}
idempotency_hits_total{endpoint}
inventory_reservations_total{result}
payment_callbacks_total{result}
outbox_events_total{status,type}
order_state_transitions_total{from,to,result}
```

Metric labels use bounded values only; session, order, participant, and staff IDs
must not be labels because they create unbounded cardinality.

## 12. Docker and delivery

### 12.1 Image

- Multi-stage Dockerfile
- Maven build/test stage and minimal Java runtime stage
- Spring Boot layered-jar extraction for cache-efficient rebuilds
- Dedicated non-root runtime user
- Read-only application artifact and writable temporary directory only
- Container-aware JVM limits and graceful shutdown
- Actuator-based health check

### 12.2 Compose profiles

`compose.yaml` starts:

- Nginx
- two application instances
- MySQL
- Redis
- RocketMQ name server and broker

`compose.observability.yaml` additionally starts:

- Prometheus
- Grafana with provisioned data source/dashboard
- OpenTelemetry Collector
- Jaeger

Dependencies expose health checks, and applications wait on healthy dependencies
rather than relying only on container start order. Configuration is supplied by
environment variables and `.env.example`; real secrets are never committed.

### 12.3 CI

GitHub Actions performs:

1. Compile and static checks.
2. Unit and architecture tests.
3. Testcontainers integration tests.
4. Docker image build.
5. Dependency and image vulnerability scan.
6. Push tagged images to GHCR on release tags.

## 13. Test strategy

### 13.1 Test layers

- Unit tests: state machines, price calculations, fingerprints, authorization
- Redis script tests: atomic mutation, replay, version conflict, freeze/recovery
- MySQL integration tests: migrations, unique constraints, CAS, inventory SQL
- API integration tests: authentication, validation, Problem Details, workflows
- MQ integration tests: outbox retry, duplicate delivery, delayed expiry
- Architecture tests: package and dependency rules
- Compose smoke test: two instances behind Nginx
- Load/concurrency tests: invariants and latency measurements

Mocks are used only at narrow external boundaries. MySQL- and Redis-specific
behavior is tested against real containers, not an in-memory substitute.

### 13.2 Required concurrency scenarios

1. One hundred mutations with unique operation IDs produce the exact quantity.
2. Replaying each operation ID does not change quantity again.
3. One hundred submissions with the same idempotency key return one order.
4. One hundred submissions with different keys for one session create one order.
5. Many sessions compete for limited inventory; reserved quantity never exceeds
   initial available inventory and stock never becomes negative.
6. One hundred duplicate payment callbacks transition and publish once.
7. Payment success races order expiry; exactly one terminal outcome wins.
8. The outbox publisher crashes after send; redelivery does not repeat business
   side effects.
9. An application crashes with a frozen cart; reconciliation finalizes or
   restores it according to durable MySQL state.
10. Staff role revocation invalidates access on both application instances.

### 13.3 Performance reporting

The repository records hardware, container limits, dataset, scenario, concurrency,
duration, throughput, P50/P95/P99, error rate, and invariant results. It compares
at least one baseline implementation with the corrected implementation. Results
are reported as local benchmark evidence, not production capacity.

## 14. Security baseline

- Adaptive password hashing and no plaintext credentials
- Separate staff and participant authentication paths
- Short-lived staff access tokens plus revocable server-side sessions
- Login rate limiting and temporary account lock
- HMAC validation and replay protection for simulated provider callbacks
- Bean Validation and allow-listed enum/state inputs
- Parameterized SQL only
- Least-privilege RBAC and method-level authorization
- Secrets via environment variables
- Sensitive-value masking in logs
- Dependency and container image scanning in CI
- Nginx request-size and timeout limits

## 15. Acceptance criteria

The design is implemented when all of the following are true:

1. `docker compose up -d` starts a healthy two-instance API and its required
   dependencies from a clean checkout.
2. OpenAPI documents every supported customer, kitchen, manager, and simulator
   endpoint with example errors.
3. A scripted flow can join a table, mutate a shared cart, submit one order,
   simulate payment, process it in the kitchen, and query completion.
4. Customer tokens cannot call staff APIs, and revoked staff sessions fail on
   both instances.
5. All required concurrency scenarios pass repeatedly without duplicate orders,
   duplicate inventory movement, or invalid state transitions.
6. Flyway can create the schema from an empty MySQL database.
7. Critical state-machine, idempotency, inventory, security, callback, outbox,
   and recovery paths have automated tests.
8. Prometheus receives application and custom metrics; one request can be found
   by trace ID in logs and Jaeger.
9. CI builds, tests, scans, and produces the Docker image.
10. README explains architecture decisions, local execution, test evidence,
    failure recovery, honest limitations, and interview talking points.

## 16. Planned architecture decisions

The implementation will record focused ADRs for:

1. Modular monolith instead of microservices
2. Java 21 and Spring Boot 3.5 baseline
3. Redis cart with freeze-and-reconcile submission
4. Database constraints plus CAS instead of distributed locks for order ownership
5. Conditional inventory updates with an idempotent ledger
6. Transactional Outbox instead of direct transactional MQ publication
7. Minimal JWT claims plus Redis-backed staff sessions
8. Docker Compose instead of Kubernetes

## 17. Delivery sequence

The implementation plan will use vertical slices so each milestone is runnable:

1. Repository baseline, build, migrations, error model, and test harness
2. Staff login, sessions, RBAC, and security tests
3. Tables, anonymous participants, and session lifecycle
4. Menu, inventory, and management setup APIs
5. Redis shared cart and atomic-operation tests
6. Idempotent order submission, inventory reservation, and recovery
7. Simulated payment, timeout cancellation, and kitchen workflow
8. Outbox/RocketMQ reliability
9. Two-instance Docker deployment and observability
10. Concurrency benchmark, documentation, and resume material

Each slice is implemented test-first, reviewed, and verified before proceeding.
