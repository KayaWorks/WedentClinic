package com.wedent.clinic.treatment.entity;

/**
 * Lifecycle of a single treatment row.
 *
 * <p>Only {@link #COMPLETED} treatments contribute to a doctor's gross
 * revenue in the payout calculation. {@link #PLANNED} captures intent
 * (visible on the patient timeline, not yet billable) and
 * {@link #CANCELLED} preserves the historical record without affecting
 * any payout total.</p>
 */
public enum TreatmentStatus {
    PLANNED,
    COMPLETED,
    CANCELLED
}
