package com.wedent.clinic.preferences.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Partial update — every field is optional and the service merges with the
 * existing row (or with the defaults if no row exists yet).
 *
 * <p>{@code notifications} is a delta map: only the keys included are
 * touched, the rest stay at their current value. Pass {@code null} for the
 * whole field to skip the notification update entirely.</p>
 *
 * <p>Validation is intentionally light — the allowed values for language /
 * timezone / format strings are too varied to encode as enums and the FE
 * already pre-filters them. The size caps mirror the column lengths so a
 * runaway payload trips here, not in JDBC.</p>
 */
public record UserPreferencesUpdateRequest(
        @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "language must be a BCP-47 tag like 'tr' or 'en-US'")
        @Size(max = 8) String language,
        @Size(max = 64) String timezone,
        @Size(max = 20) String dateFormat,
        @Size(max = 8) String timeFormat,
        @Size(max = 8) String currency,
        Channels channels,
        Map<String, Boolean> notifications
) {

    public record Channels(Boolean email, Boolean sms, Boolean inApp) {}
}
