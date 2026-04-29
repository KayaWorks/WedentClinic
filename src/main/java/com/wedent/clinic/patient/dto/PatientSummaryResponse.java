package com.wedent.clinic.patient.dto;

import java.math.BigDecimal;

/**
 * Aggregated snapshot of a patient's clinical + financial standing.
 * All monetary values use the same {@code currency} (always TRY for now).
 *
 * <p>Returned by {@code GET /api/patients/{id}/summary}.</p>
 */
public record PatientSummaryResponse(
        Long patientId,
        long plannedTreatmentCount,
        long completedTreatmentCount,
        long cancelledTreatmentCount,
        BigDecimal totalTreatmentAmount,   // sum of COMPLETED treatment fees
        BigDecimal paidAmount,             // sum of COMPLETED payments
        BigDecimal balance,                // paidAmount − totalTreatmentAmount
        String currency
) {}
