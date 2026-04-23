package com.wedent.clinic.security.blacklist;

import java.time.Instant;

/**
 * Revocation list for otherwise-stateless access tokens.
 *
 * <p>Access JWTs are stateless by design, but "immediate logout" requires
 * server-side revocation.  We punt the cost to Redis: a blacklisted
 * {@code jti} lives in the cache only until its natural expiration, so the
 * set size tracks concurrent active users, not all-time logouts.</p>
 */
public interface AccessTokenBlacklist {

    /**
     * Mark a token as revoked.  Implementations set a Redis key with TTL =
     * {@code expiresAt - now}; once the TTL lapses, the entry auto-evicts
     * and the check becomes free again.
     *
     * @param jti       unique token id (from the JWT {@code jti} claim)
     * @param expiresAt original token expiry (never extend past this — the
     *                  token will stop validating on its own anyway)
     */
    void blacklist(String jti, Instant expiresAt);

    /** Cheap lookup used by the auth filter on every request. */
    boolean isBlacklisted(String jti);
}
