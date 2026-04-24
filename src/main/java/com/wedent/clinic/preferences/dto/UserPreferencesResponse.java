package com.wedent.clinic.preferences.dto;

import java.util.Map;

/**
 * Effective preferences for the caller. {@code notifications} is a flat
 * {@code event → enabled} map so the FE can render checkboxes without
 * unpacking nested structures.
 */
public record UserPreferencesResponse(
        String language,
        String timezone,
        String dateFormat,
        String timeFormat,
        String currency,
        Channels channels,
        Map<String, Boolean> notifications
) {

    public record Channels(boolean email, boolean sms, boolean inApp) {}
}
