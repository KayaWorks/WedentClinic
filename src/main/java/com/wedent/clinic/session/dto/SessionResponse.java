package com.wedent.clinic.session.dto;

import java.time.Instant;

/**
 * One live device/session for the calling user.
 *
 * <p>{@code sessionId} is the SHA-256 hex of the underlying refresh token —
 * opaque to the FE and impossible to reverse into a working credential, but
 * stable enough to round-trip into the revoke endpoint.</p>
 */
public record SessionResponse(
        String sessionId,
        Instant issuedAt,
        Instant expiresAt,
        String ipAddress,
        String userAgent
) {}
