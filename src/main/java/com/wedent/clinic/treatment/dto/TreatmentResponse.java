package com.wedent.clinic.treatment.dto;

import com.wedent.clinic.treatment.entity.TreatmentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read-side projection of a treatment. The doctor's display name is
 * flattened into a single {@code doctorName} so the patient timeline
 * doesn't need a second round-trip to /api/employees/{id}.
 */
public record TreatmentResponse(
        Long id,
        Long patientId,
        Long doctorId,
        String doctorName,
        Long clinicId,
        Long companyId,
        String name,
        String toothNumber,
        String notes,
        Instant performedAt,
        /** Set when status transitions to COMPLETED. */
        Instant completedAt,
        BigDecimal fee,
        String currency,
        TreatmentStatus status,
        Instant payoutLockedAt,
        Instant createdAt,
        Instant updatedAt
) {}
