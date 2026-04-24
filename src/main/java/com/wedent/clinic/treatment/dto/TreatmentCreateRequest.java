package com.wedent.clinic.treatment.dto;

import com.wedent.clinic.treatment.entity.TreatmentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Treatment creation payload. {@code patientId} is taken from the URL,
 * not the body — the controller is mounted under
 * {@code /api/patients/{patientId}/treatments}.
 *
 * <p>{@code currency} and {@code status} are optional — defaults are
 * applied service-side ({@code TRY} and {@code PLANNED} respectively).
 * The FE is expected to send {@code COMPLETED} only when the procedure
 * is actually done, since that's what feeds the payout aggregation.</p>
 */
public record TreatmentCreateRequest(
        @NotNull Long doctorId,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 8) String toothNumber,
        @Size(max = 2000) String notes,
        @NotNull Instant performedAt,
        @NotNull
        @DecimalMin(value = "0.01", message = "fee must be positive")
        @Digits(integer = 10, fraction = 2)
        BigDecimal fee,
        @Size(max = 8) String currency,
        TreatmentStatus status
) {}
