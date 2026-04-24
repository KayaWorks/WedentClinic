package com.wedent.clinic.preferences.service;

/**
 * Single source of truth for default preference values. Mirrored in the
 * {@code V8__user_preferences.sql} column defaults so a row inserted by
 * either path lands at the same starting state.
 */
public final class PreferenceDefaults {

    public static final String LANGUAGE     = "tr";
    public static final String TIMEZONE     = "Europe/Istanbul";
    public static final String DATE_FORMAT  = "DD.MM.YYYY";
    public static final String TIME_FORMAT  = "HH:mm";
    public static final String CURRENCY     = "TRY";

    public static final boolean NOTIFY_EMAIL  = true;
    public static final boolean NOTIFY_SMS    = false;
    public static final boolean NOTIFY_IN_APP = true;

    /** Empty by design — opt-ins are populated as the FE adds events. */
    public static final String NOTIFICATIONS_JSON = "{}";

    private PreferenceDefaults() {}
}
