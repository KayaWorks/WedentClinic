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
    /** User revoked a single live session of theirs from the device list. */
    SESSION_REVOKED,
    /** User wiped every other live session — "log out everywhere" action. */
    SESSIONS_REVOKED_ALL,

    // --- Appointment lifecycle (high-risk / PHI-adjacent) ---
    APPOINTMENT_CREATED,
    APPOINTMENT_UPDATED,
    APPOINTMENT_STATUS_CHANGED,
    APPOINTMENT_DELETED,

    // --- User/role administration ---
    USER_CREATED,
    /** Admin-side profile edit (name, phone, clinic re-assignment, employee type, doctor profile). */
    USER_UPDATED,
    USER_DISABLED,
    /** Admin reactivated a disabled account — inverse of {@link #USER_DISABLED}. */
    USER_ACTIVATED,
    /** Admin-generated temporary password; always revokes every live session. */
    USER_PASSWORD_RESET,
    USER_ROLE_CHANGED,
    /** Self-service password change — all live sessions revoked as a side effect. */
    USER_PASSWORD_CHANGED,

    // --- Tenant topology (company + clinic lifecycle) ---
    /** Owner-only: a new clinic was provisioned under the company. */
    CLINIC_CREATED,
    /** Owner-only: clinic metadata (name, address, contact) was edited. */
    CLINIC_UPDATED,
    /** Owner-only: clinic soft-deleted. Does not cascade to sub-resources. */
    CLINIC_DELETED,
    /** Owner-only: company profile fields (name, contact, taxNumber) updated. */
    COMPANY_UPDATED,

    // --- Treatment lifecycle (feeds payout aggregation) ---
    TREATMENT_CREATED,
    TREATMENT_UPDATED,
    TREATMENT_STATUS_CHANGED,
    TREATMENT_DELETED,

    // --- Payout (hakediş) lifecycle ---
    /** DRAFT row materialized for a doctor + date window. */
    PAYOUT_DRAFT_CREATED,
    /** DRAFT payout gross/net refreshed from current treatment state. */
    PAYOUT_RECALCULATED,
    /** DRAFT→APPROVED transition. Included treatments are locked. */
    PAYOUT_APPROVED,
    /** APPROVED→PAID transition. */
    PAYOUT_MARKED_PAID,
    /** DRAFT→CANCELLED (wrong draft, abandoned). */
    PAYOUT_CANCELLED,
    PAYOUT_DEDUCTION_ADDED,
    PAYOUT_DEDUCTION_REMOVED,

    // --- Patient Notes ---
    PATIENT_NOTE_CREATED,
    PATIENT_NOTE_UPDATED,
    PATIENT_NOTE_DELETED,

    // --- Payments ---
    PAYMENT_CREATED,
    PAYMENT_CANCELLED
}
