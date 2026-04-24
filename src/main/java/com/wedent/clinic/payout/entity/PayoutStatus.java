package com.wedent.clinic.payout.entity;

/**
 * Lifecycle of a single payout period.
 *
 * <pre>
 *   DRAFT ──► APPROVED ──► PAID   (happy path)
 *     └────► CANCELLED            (wrong draft, abandoned)
 * </pre>
 *
 * <p>DRAFT is the only mutable state: deductions can be added/removed
 * and the period can be recalculated. APPROVE locks included treatments
 * and snapshots the commission rate + treatment total so later edits
 * on the source rows can never shift an issued payout.</p>
 */
public enum PayoutStatus {
    DRAFT,
    APPROVED,
    PAID,
    CANCELLED
}
