-- =============================================================
-- V7: Append-only security audit log
-- =============================================================
-- Intentionally NOT extending BaseEntity conventions:
--   * No `updated_at`, `version`, `active`              → append-only, never mutated.
--   * No FK to users/companies/clinics                  → we log ids even if those rows
--                                                         are later hard-deleted.
--   * `detail` is JSONB for ad-hoc structured context   → (e.g. {"reason":"BAD_PASSWORD"}).
-- =============================================================

CREATE TABLE audit_log (
    id              BIGSERIAL    PRIMARY KEY,
    event_type      VARCHAR(64)  NOT NULL,
    actor_user_id   BIGINT,
    actor_email     VARCHAR(200),
    company_id      BIGINT,
    clinic_id       BIGINT,
    target_type     VARCHAR(64),
    target_id       BIGINT,
    detail          JSONB,
    ip_address      VARCHAR(64),
    trace_id        VARCHAR(128),
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Investigation hot-paths:
--   "what did user X do in the last 24h"
CREATE INDEX idx_audit_log_actor_time
    ON audit_log (actor_user_id, occurred_at DESC);

--   "show me all failed logins for a tenant"
CREATE INDEX idx_audit_log_company_event_time
    ON audit_log (company_id, event_type, occurred_at DESC);

--   "correlate an incident by request id"
CREATE INDEX idx_audit_log_trace
    ON audit_log (trace_id)
    WHERE trace_id IS NOT NULL;
