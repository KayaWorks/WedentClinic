-- =============================================================
-- V8: Per-user UI + notification preferences
-- =============================================================
-- One row per user. Created lazily — defaults are returned by the service
-- when no row exists yet, so existing users don't need a backfill insert.
--
-- Design notes:
--   * General UI prefs (language, timezone, formats, currency) live as
--     explicit columns — they are stable, queryable, and validated against
--     a small known set of values.
--   * Notification settings live in `notifications` JSONB so we can add
--     new event categories (appointment reminders, security alerts, ...)
--     without a migration each time the FE introduces another toggle.
--   * Channel-level enables (email / sms / inApp) get their own columns
--     because they gate the JSONB events — querying "send me everyone who
--     has email opted in" must stay an indexed boolean lookup, not a JSON
--     probe.
--   * `updated_at` is BaseEntity-style so the FE can show "last saved" and
--     so we can hook auditing into changes later if needed.
-- =============================================================

CREATE TABLE user_preferences (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    -- General UI prefs
    language      VARCHAR(8)   NOT NULL DEFAULT 'tr',
    timezone      VARCHAR(64)  NOT NULL DEFAULT 'Europe/Istanbul',
    date_format   VARCHAR(20)  NOT NULL DEFAULT 'DD.MM.YYYY',
    time_format   VARCHAR(8)   NOT NULL DEFAULT 'HH:mm',
    currency      VARCHAR(8)   NOT NULL DEFAULT 'TRY',
    -- Notification channel toggles
    notify_email  BOOLEAN      NOT NULL DEFAULT TRUE,
    notify_sms    BOOLEAN      NOT NULL DEFAULT FALSE,
    notify_in_app BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Per-event opt-ins (free-form)
    notifications JSONB        NOT NULL DEFAULT '{}'::jsonb,
    -- BaseEntity bookkeeping (no soft-delete: prefs are always live)
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by    BIGINT,
    updated_by    BIGINT,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    version       BIGINT       NOT NULL DEFAULT 0
);

-- The unique constraint above already creates an index on user_id; no
-- additional indexes needed — every read is a single-row lookup by user.
