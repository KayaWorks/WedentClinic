package com.wedent.clinic.session.service;

import com.wedent.clinic.session.dto.SessionResponse;

import java.util.List;

/**
 * Self-service device/session management for the authenticated caller.
 *
 * <p>Every method scopes to the current security principal — there is no
 * "list sessions for user X" admin path here. That keeps the surface small
 * and makes accidental cross-user revokes structurally impossible.</p>
 */
public interface SessionService {

    /** Live (non-revoked, non-expired) sessions for the caller, newest first. */
    List<SessionResponse> listMine();

    /**
     * Revokes a single session belonging to the caller. Returns silently if
     * the session id is unknown / already revoked / belongs to someone else
     * — the controller can map a {@code false} return to a 404 if it wants
     * to differentiate, but the default is idempotent.
     *
     * @return {@code true} when a live session was actually revoked
     */
    boolean revokeMine(String sessionId);

    /**
     * "Log out everywhere" — revokes every live session for the caller,
     * including the one the request itself rode in on. The FE is expected
     * to clear local credentials right after.
     *
     * @return number of sessions actually revoked
     */
    int revokeAllMine();
}
