package com.wedent.clinic.common.audit.event;

/**
 * Closed set of audit event types. Enumerating them explicitly makes
 * downstream queries ("count failed logins per company in the last hour")
 * stable and typo-proof, and gives dashboards a clean grouping key.
 */
public enum AuditEventType {

    // --- Authentication ---
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGIN_RATE_LIMITED,
    LOGOUT,
    TOKEN_REFRESHED,
    /** Presented refresh token was already rotated — likely theft. */
    TOKEN_REFRESH_REPLAY,

    // --- Appointment lifecycle (high-risk / PHI-adjacent) ---
    APPOINTMENT_CREATED,
    APPOINTMENT_UPDATED,
    APPOINTMENT_STATUS_CHANGED,
    APPOINTMENT_DELETED,

    // --- User/role administration (reserved for future user-mgmt endpoints) ---
    USER_CREATED,
    USER_DISABLED,
    USER_ROLE_CHANGED,
    /** Self-service password change — all live sessions revoked as a side effect. */
    USER_PASSWORD_CHANGED
}
