package com.wedent.clinic.preferences.service;

import com.wedent.clinic.preferences.dto.UserPreferencesResponse;
import com.wedent.clinic.preferences.dto.UserPreferencesUpdateRequest;

/**
 * Per-user UI + notification preferences. Always operates on the
 * authenticated caller — there is no admin override and no
 * "preferences for user X" lookup, since prefs are by definition
 * personal-context.
 */
public interface UserPreferencesService {

    UserPreferencesResponse getCurrent();

    /**
     * Partial update with delta semantics. Fields not present in the
     * request stay at their current value; the {@code notifications} map is
     * merged key-by-key into the stored map rather than replacing it
     * wholesale.
     */
    UserPreferencesResponse updateCurrent(UserPreferencesUpdateRequest request);
}
