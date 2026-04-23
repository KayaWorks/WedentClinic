package com.wedent.clinic.user.service;

import com.wedent.clinic.user.dto.PasswordChangeRequest;
import com.wedent.clinic.user.dto.UserProfileResponse;
import com.wedent.clinic.user.dto.UserProfileUpdateRequest;

/**
 * Self-service profile operations for the currently-authenticated user.
 *
 * <p>No {@code userId} arguments on purpose — the callee always resolves the
 * caller from the security context, which keeps the surface narrow: an
 * attacker cannot trick a profile update into targeting someone else's row.</p>
 */
public interface UserProfileService {

    /** Fetches the caller's profile (roles + permissions flattened for FE guards). */
    UserProfileResponse getCurrent();

    /** Updates the caller's profile-level fields. Returns the fresh view. */
    UserProfileResponse updateCurrent(UserProfileUpdateRequest request);

    /**
     * Rotates the caller's password. Requires the current password as a
     * second factor so a stolen access token can't silently take over the
     * account. Side-effect: every live refresh-token session for the user
     * is revoked — the caller must log in again on all devices.
     */
    void changePassword(PasswordChangeRequest request, String ipAddress);
}
