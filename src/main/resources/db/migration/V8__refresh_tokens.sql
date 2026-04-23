-- =============================================================
-- V8: Refresh tokens (server-side session)
-- =============================================================
-- Design notes:
--   * We never store the raw token value — only a SHA-256 hex
--     digest.  A DB dump then doesn't leak live sessions.
--   * `replaced_by_id` enables rotation-with-detection: if a
--     refresh token is presented AFTER it has been rotated,
--     we can walk the chain, flag reuse, and nuke the whole
--     chain for that user (classic refresh-token-replay defense).
--   * Not extending BaseEntity — no soft-delete, no `active`
--     flag; revocation is a timestamp so we can audit *when*.
--   * FK on user_id uses ON DELETE CASCADE so a hard-deleted
--     user doesn't leave orphan sessions behind.
-- =============================================================

CREATE TABLE refresh_tokens (
    id                BIGSERIAL    PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash        VARCHAR(64)  NOT NULL,
    issued_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMPTZ  NOT NULL,
    revoked_at        TIMESTAMPTZ,
    replaced_by_id    BIGINT       REFERENCES refresh_tokens(id) ON DELETE SET NULL,
    ip_address        VARCHAR(64),
    user_agent        VARCHAR(255)
);

-- Lookup hot-path on /api/auth/refresh: exact match on hash.
CREATE UNIQUE INDEX uk_refresh_tokens_hash
    ON refresh_tokens (token_hash);

-- "revoke all sessions of user X" and list-active-sessions endpoints.
CREATE INDEX idx_refresh_tokens_user
    ON refresh_tokens (user_id, revoked_at, expires_at);
