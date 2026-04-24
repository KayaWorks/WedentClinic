package com.wedent.clinic.payout.dto;

import com.wedent.clinic.payout.entity.PayoutDeductionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Add a deduction line to a DRAFT payout. Positive amounts only —
 * the CHECK constraint on {@code payout_deductions.amount} is the
 * last line of defense after the Bean-Validation annotation here.
 */
public record PayoutDeductionRequest(
        @NotNull PayoutDeductionType type,
        @Size(max = 500) String description,
        @NotNull
        @DecimalMin(value = "0.01", message = "amount must be positive")
        @Digits(integer = 10, fraction = 2)
        BigDecimal amount
) {}
