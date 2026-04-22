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

    // --- Appointment lifecycle (high-risk / PHI-adjacent) ---
    APPOINTMENT_CREATED,
    APPOINTMENT_UPDATED,
    APPOINTMENT_STATUS_CHANGED,
    APPOINTMENT_DELETED,

    // --- User/role administration (reserved for future user-mgmt endpoints) ---
    USER_CREATED,
    USER_DISABLED,
    USER_ROLE_CHANGED
}
