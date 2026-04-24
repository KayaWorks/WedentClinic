package com.wedent.clinic.payout.entity;

/**
 * Closed set of deduction categories used on {@link PayoutDeduction}.
 *
 * <p>Kept stable + low-cardinality so reporting can group ("what did
 * we spend on lab work last quarter?") without string-matching.</p>
 */
public enum PayoutDeductionType {
    /** Lab work (crowns, bridges, aligners sent out). */
    LAB,
    /** Consumables used per-procedure (composites, anesthetic, burs). */
    MATERIAL,
    /** Money already advanced to the doctor to be netted out this period. */
    ADVANCE_PAYMENT,
    /** Disciplinary or contractual penalty. */
    PENALTY,
    /** Fallback for anything that doesn't fit — description required in practice. */
    OTHER
}
