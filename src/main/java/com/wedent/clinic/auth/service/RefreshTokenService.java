package com.wedent.clinic.auth.service;

import com.wedent.clinic.auth.entity.RefreshToken;
import com.wedent.clinic.user.entity.User;

public interface RefreshTokenService {

    /**
     * Issues a brand-new refresh token for a user.  Returns both the
     * persisted row and the raw token value that must be handed to the
     * client (the raw value is never stored, only its SHA-256 digest).
     */
    Issued issue(User user, String ipAddress, String userAgent);

    /**
     * Validates a presented refresh token, revokes it, issues a replacement,
     * and returns the replacement.  Handles rotation-with-replay-detection.
     */
    Rotated rotate(String rawRefreshToken, String ipAddress, String userAgent);

    /**
     * Revokes a single refresh token.  Silent no-op if the token is not
     * recognised or already revoked — prevents user enumeration via logout.
     */
    void revoke(String rawRefreshToken);

    /** Refresh-token TTL, in milliseconds. Client uses this for UX ("remember-me"). */
    long expirationMillis();

    record Issued(RefreshToken row, String rawToken) {}

    record Rotated(RefreshToken newRow, String newRawToken, Long userId) {}
}
