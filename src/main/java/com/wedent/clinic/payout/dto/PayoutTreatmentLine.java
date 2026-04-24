package com.wedent.clinic.payout.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Compact view of one treatment included in a payout. Surfaced in
 * {@link PayoutResponse#treatments()} so the FE can render the
 * "hangi işlemler dahil" kalemli listesi without a second roundtrip.
 */
public record PayoutTreatmentLine(
        Long treatmentId,
        Long patientId,
        String patientName,
        String treatmentName,
        Instant completedAt,
        BigDecimal fee
) {}
