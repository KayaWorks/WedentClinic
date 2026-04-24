package com.wedent.clinic.auth.service;

import com.wedent.clinic.user.entity.User;

import java.time.Instant;
import java.util.List;

/**
 * Refresh-token lifecycle with Redis as the sole backing store.
 *
 * <p>Why Redis (and not Postgres): refresh tokens are short-lived, high-churn
 * session state — every login creates one, every refresh rotates one, every
 * logout deletes one.  A KV store with native TTLs eliminates the housekeeping
 * job that a relational table would need (daily sweep of expired rows) and
 * keeps session writes off the primary DB's write path.
 */
public interface RefreshTokenService {

    /** Issues a brand-new token for a user, returning only the raw value. */
    Issued issue(User user, String ipAddress, String userAgent);

    /**
     * Validates a presented refresh token, marks it as rotated, issues a
     * replacement, and returns the replacement + the owning user id.
     *
     * <p>Implements rotation-with-replay-detection: if a token that has
     * already been rotated is re-presented the implementation revokes every
     * live session for that user (classic "stolen refresh token" defense).
     */
    Rotated rotate(String rawRefreshToken, String ipAddress, String userAgent);

    /** Revokes a single refresh token.  Silent no-op on unknown/expired tokens. */
    void revoke(String rawRefreshToken);

    /** Revokes every active refresh token for a user.  Used by admin flows / password change. */
    int revokeAllForUser(Long userId);

    /**
     * Lists every <em>live</em> session for a user (i.e. not yet revoked,
     * not yet expired). Driven by the per-user {@code refresh:u:{userId}}
     * SET; cleans up dangling members whose underlying record has expired.
     */
    List<SessionView> listSessions(Long userId);

    /**
     * Revokes a single session belonging to {@code userId}, identified by
     * the opaque {@code sessionId} surfaced by {@link #listSessions}.
     *
     * <p>Returns {@code true} when a live session was actually revoked,
     * {@code false} when the session is unknown / already revoked / belongs
     * to another user. Never throws on unknown ids — the caller decides
     * whether to surface a 404 or treat it as idempotent.</p>
     */
    boolean revokeSession(Long userId, String sessionId);

    /** Refresh-token TTL in milliseconds, derived from config. */
    long expirationMillis();

    record Issued(String rawToken) {}

    record Rotated(String newRawToken, Long userId) {}

    /**
     * Public projection of a live refresh-token record. The {@code sessionId}
     * is the SHA-256 hex of the raw refresh token — opaque, one-way, and
     * therefore safe to surface to the FE for revoke calls.
     */
    record SessionView(
            String sessionId,
            Instant issuedAt,
            Instant expiresAt,
            String ipAddress,
            String userAgent
    ) {}
}
