package com.wedent.clinic.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Inline DoctorProfile payload used by both the admin create and admin
 * patch endpoints. All fields are optional: at create time the service
 * fills in sane defaults (null commission rate → payouts treat as 0),
 * at patch time a null field means "leave it alone" (handled at the
 * service level, not here, so callers can explicitly null things out
 * with {@code null} when they do send it).
 */
public record DoctorProfilePayload(
        @Size(max = 150) String specialty,
        @DecimalMin(value = "0.00", message = "commissionRate must be >= 0")
        @DecimalMax(value = "100.00", message = "commissionRate must be <= 100")
        @Digits(integer = 3, fraction = 2)
        BigDecimal commissionRate,
        @DecimalMin(value = "0.00", message = "fixedSalary must be >= 0")
        @Digits(integer = 13, fraction = 2)
        BigDecimal fixedSalary
) {}
