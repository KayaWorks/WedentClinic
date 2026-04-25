# Wedent Clinic — Dental Clinic Patient Tracking Backend

Spring Boot 3 / Java 21 backend for a multi-tenant dental clinic management system.

## Stack

- Java 21, Spring Boot 3.3.5, Maven
- Spring Web, Validation, Security (JWT, stateless), Data JPA
- PostgreSQL 16 + Flyway migrations (V1–V7)
- Redis 7 (Spring Data Redis + Spring Cache): refresh-token store, JWT blacklist, login rate limiter, general `@Cacheable` layer
- Lombok, MapStruct (shared `CommonMapperConfig`), springdoc-openapi
- JUnit 5, Mockito, Spring Security Test
- Testcontainers (Postgres + Redis) for integration tests; H2 only for legacy smoke tests

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
| `railway` | Railway runtime profile with capped pools/threads, lazy init, health/info actuator only |

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

Failed logins are rate-limited per `(clientIp, email)` — 10 failures / 10 min window, reset on a successful login. The limiter is Redis-backed (atomic `INCR` + one-shot `EXPIRE` on the first miss of the window), so horizontal scale-out works out of the box.

## CORS

Browser clients must be explicitly allow-listed because credentials are enabled. Configure frontend origins with a comma-separated environment variable:

```bash
APP_CORS_ALLOWED_ORIGINS=https://clinicflow-dashboard-production.up.railway.app,http://localhost:5173,http://localhost:3000
```

If the variable is not set, the Railway frontend plus local Vite defaults are allowed: `https://clinicflow-dashboard-production.up.railway.app`, `http://localhost:5173`, `http://127.0.0.1:5173`, `http://localhost:3000`, and `http://127.0.0.1:3000`.

## Railway deployment

The repository includes `railway.json` so Railway starts the jar with an explicit memory-capped JVM command while still honoring `JAVA_OPTS` and `PORT`:

```bash
java ${JAVA_OPTS:--Xms128m -Xmx384m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m -Dfile.encoding=UTF-8} -Dserver.port=${PORT:-8080} -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-railway} -jar target/*.jar
```

Recommended Railway variables:

```bash
SPRING_PROFILES_ACTIVE=railway
JAVA_OPTS=-Xms128m -Xmx384m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m -Dfile.encoding=UTF-8
DB_URL=jdbc:postgresql://YOUR-POSTGRES-HOST:PORT/YOUR_DATABASE
DB_USERNAME=YOUR_DATABASE_USER
DB_PASSWORD=YOUR_DATABASE_PASSWORD
REDIS_URL=redis://default:YOUR_REDIS_PASSWORD@YOUR_REDIS_HOST:PORT
JWT_SECRET=replace-with-a-strong-256-bit-or-larger-secret
APP_CORS_ALLOWED_ORIGINS=https://clinicflow-dashboard-production.up.railway.app,http://localhost:5173,http://localhost:3000
```

If the service still hits Railway memory limits, raise only the heap cap first:

```bash
JAVA_OPTS=-Xms128m -Xmx512m -XX:+UseSerialGC -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=96m -Dfile.encoding=UTF-8
```

The `railway` profile keeps Flyway enabled, disables Swagger/OpenAPI endpoints, exposes only `health,info` actuator endpoints, and starts with small Hikari/Tomcat defaults (`DB_POOL_SIZE=3`, `TOMCAT_MAX_THREADS=50`).

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

Refresh tokens moved to Redis (see [Redis / cache](#redis--cache)); there is no `refresh_tokens` table.

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
- Refresh tokens (`/api/auth/refresh`, `/api/auth/logout`) are 256-bit random values handed to clients; only SHA-256 hashes are persisted (in Redis). Rotation on use + replay detection (nukes every live session for the user) defends against stolen-token replay. Lifetime via `JWT_REFRESH_EXPIRATION_DAYS` (default 14).
- Access-token revocation: `/api/auth/logout` also blacklists the current access token's `jti` in Redis until its natural expiry; `JwtAuthenticationFilter` rejects any token whose `jti` is blacklisted.

## Redis / cache

Redis is a hard dependency — the app fails to start without it. Configure via `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` (see `application.yml`). All keys are namespaced with a configurable prefix (default `wedent:`).

| Key pattern                         | Purpose                                                                 |
|-------------------------------------|-------------------------------------------------------------------------|
| `wedent:refresh:t:{sha256(token)}`  | Per-refresh-token record (JSON). TTL = token expiry. Revoked records kept until TTL so replay attempts still fire. |
| `wedent:refresh:u:{userId}`         | SET of active hashes for the user — powers `logout-all`.                |
| `wedent:jwt:bl:{jti}`               | Access-token blacklist; TTL = remaining lifetime of the access token.   |
| `wedent:rl:login:{ip\|email}`       | Login rate limiter counter; 10-minute sliding window.                   |
| `wedent:cache:*`                    | Spring Cache (`@Cacheable`) namespace with Jackson JSON serializer.     |

Tunables live under `app.redis`:

```yaml
app:
  redis:
    key-prefix: "wedent:"
    default-ttl: 10m
```

Integration tests spin a Redis container (`redis:7-alpine`) alongside Postgres via the shared `AbstractPostgresIntegrationTest` base class — no local Redis install needed.

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

- Scheduled worker: appointment reminders (email/SMS)
- Audit log retention policy + cold-storage export job
- User/role administration endpoints (wired up, but admin UI pending)
- `/api/users/me` + self-service password change
- Dashboard summary endpoint (counts + recent activity) for the web shell
