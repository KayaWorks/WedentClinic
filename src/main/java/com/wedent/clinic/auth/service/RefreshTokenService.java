package com.wedent.clinic.auth.service;

import com.wedent.clinic.user.entity.User;

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

    /** Refresh-token TTL in milliseconds, derived from config. */
    long expirationMillis();

    record Issued(String rawToken) {}

    record Rotated(String newRawToken, Long userId) {}
}
