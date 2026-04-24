package com.wedent.clinic.treatment.dto;

import com.wedent.clinic.treatment.entity.TreatmentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Partial update — every field is optional, the service only touches the
 * ones that arrive non-null. Re-assigning {@code doctorId} is allowed
 * until the treatment is payout-locked, since clinics occasionally
 * correct who actually performed a procedure after the fact.
 */
public record TreatmentUpdateRequest(
        Long doctorId,
        @Size(max = 200) String name,
        @Size(max = 8) String toothNumber,
        @Size(max = 2000) String notes,
        Instant performedAt,
        @DecimalMin(value = "0.01", message = "fee must be positive")
        @Digits(integer = 10, fraction = 2)
        BigDecimal fee,
        @Size(max = 8) String currency,
        TreatmentStatus status
) {}
