# Wedent Clinic — Dental Clinic Patient Tracking Backend

Spring Boot 3 / Java 21 backend for a multi-tenant dental clinic management system.

## Stack

- Java 21, Spring Boot 3.3.5, Maven
- Spring Web, Validation, Security (JWT, stateless), Data JPA
- PostgreSQL 16 + Flyway migrations (V1–V8)
- Lombok, MapStruct (shared `CommonMapperConfig`), springdoc-openapi
- JUnit 5, Mockito, Spring Security Test
- Testcontainers (Postgres) for integration tests; H2 only for legacy smoke tests

## Multi-tenant model

`Company` (tenant root) → `Clinic`s. Every business entity (User, Employee, Patient, Appointment) carries `company_id` and usually `clinic_id`. Scope is enforced in the service layer via `SecurityUtils` reading from the JWT-bound `AuthenticatedUser`, and centralized in `TenantScopeResolver` for list/search operations so a non-owner is always clamped to their own clinic.

## Roles

`CLINIC_OWNER`, `MANAGER`, `DOCTOR`, `STAFF` — seeded in `V2__rbac_seed.sql` together with default permissions. `CLINIC_OWNER` bypasses clinic-scope checks; everyone else is clamped.

## Profiles

| Profile | Purpose                                                    |
|---------|------------------------------------------------------------|
| `dev`   | Local Postgres, verbose logs, SQL formatting, bootstrap seed enabled |
| `it`    | Testcontainers-backed integration tests                    |
| `test`  | H2 smoke test (legacy; being phased out in favor of `it`)   |
| `prod`  | Env-driven config, restricted error/health detail, management port split |

Set via `SPRING_PROFILES_ACTIVE`.

## Run locally

```bash
# 1. Postgres up (any supported version >= 13)
createdb wedent

# 2. Run with dev profile (default)
./mvnw spring-boot:run
# or explicitly:
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

On first startup the `dev` bootstrap seed creates:

- Company: `Wedent Demo`
- Clinic:  `Wedent Main Clinic`
- Owner:   `owner@wedent.local` / `ChangeMe!123`

Everything is idempotent — if the owner already exists the password is left alone.

App root: http://localhost:8080
Swagger UI: http://localhost:8080/swagger-ui.html
Health: http://localhost:8080/actuator/health

## Auth flow

1. `POST /api/auth/login` with `{ email, password }` → JWT in `data.accessToken`
2. Send `Authorization: Bearer <token>` on subsequent calls
3. `X-Request-Id` (echoed back) correlates every log line with a request via MDC

Failed logins are rate-limited per `(clientIp, email)` — 10 failures / 10 min window, reset on a successful login. Replace the in-memory limiter with Redis if you scale horizontally.

## Concurrency-safe appointment booking

Conflict detection uses a two-step guard inside a single transaction:

1. `pg_advisory_xact_lock(key)` keyed on `doctorId + date` — serializes all bookings for a doctor on a given day. Plain `SELECT ... FOR UPDATE` cannot lock an empty range in Postgres, which would otherwise let two concurrent first-bookings slip through.
2. `findConflictsForUpdate` — range query for time overlap; raises `AppointmentConflictException` if any row matches.

This behaviour is exercised end-to-end by `AppointmentConflictRaceIT` against a real Postgres container.

## Migrations

All schema changes live under `src/main/resources/db/migration`:

| Version | Description                                          |
|---------|------------------------------------------------------|
| V1      | Base tables: companies, clinics, users, roles, permissions |
| V2      | RBAC seed: roles + permissions + mappings            |
| V3      | Employees + DoctorProfile                            |
| V4      | Patients                                             |
| V5      | Appointments + single-column indexes                 |
| V6      | Composite / partial indexes for hot query paths      |
| V7      | Append-only `audit_log` table (JSONB detail column)  |
| V8      | `refresh_tokens` (hashed, rotation with replay-detection) |

## Tests

```bash
# unit tests only (fast, no containers)
./mvnw test -Dsurefire.includes='**/*Test.java'

# integration tests (Testcontainers spins up postgres:16-alpine once)
./mvnw test -Dsurefire.includes='**/*IT.java'

# everything
./mvnw verify
```

Integration tests require a Docker socket (Testcontainers). Containers start once per JVM (`withReuse(true)`) — you can also enable Testcontainers' global reuse by creating `~/.testcontainers.properties` with `testcontainers.reuse.enable=true`.

## Operational notes

- Graceful shutdown is enabled (`server.shutdown=graceful`, 25s grace). K8s: set `terminationGracePeriodSeconds >= 30`.
- `server.forward-headers-strategy=framework` respects `X-Forwarded-*` behind a proxy.
- Logs include `traceId`, HTTP method and path in every line via `RequestContextFilter` + MDC.
- Prod exposes actuator on a separate `MANAGEMENT_PORT` (default 8081); keep that behind the cluster-only network.
- `/actuator/prometheus` emits Micrometer metrics with common tags `application`, `profile`; HTTP server timings publish a histogram for server-side p95/p99 calculation.
- Security audit log is append-only (`audit_log` table, V7). Events are published synchronously from the service layer and persisted **after commit** on a dedicated `auditExecutor` pool (see `AsyncConfig`) so the request hot-path is never blocked. Failures on the audit side are logged at WARN and swallowed — losing an audit row must not cascade into a failed user request.
- OpenAPI/Swagger UI shows full error-response documentation (400/401/403/404/409/422/429/500) for every endpoint via `OpenApiErrorExamplesCustomizer`.
- Refresh tokens (`/api/auth/refresh`, `/api/auth/logout`) are 256-bit random values handed to clients; only SHA-256 hashes are persisted. Rotation on use + replay detection (nukes every live session for the user) defends against stolen-token replay. Lifetime via `JWT_REFRESH_EXPIRATION_DAYS` (default 14).

## Module map

```
config       OpenAPI (+ error-examples customizer), Metrics, Async, JPA auditing, BootstrapDataRunner (dev seed)
common       BaseEntity, exception, dto, mapper config, tenant scope, web filters, audit log
security     JWT provider/filter, SecurityConfig, RBAC helpers, login rate limiter
auth         Login endpoint
user         User / Role / Permission domain
company      Company (tenant root)
clinic       Clinic (tenant child)
employee     Employees + DoctorProfile
patient      Patient CRUD + search
appointment  Appointments + conflict-safe scheduling
```

## Roadmap

- Refresh tokens + token revocation list (Redis)
- Horizontal-safe rate limiting (Redis)
- Scheduled worker: appointment reminders (email/SMS)
- Audit log retention policy + cold-storage export job
- User/role administration endpoints (wired up, but admin UI pending)
