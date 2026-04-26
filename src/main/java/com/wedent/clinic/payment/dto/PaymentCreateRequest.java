package com.wedent.clinic.payment.dto;

import com.wedent.clinic.payment.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentCreateRequest(

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 12, fraction = 2)
        BigDecimal amount,

        @Size(max = 8)
        String currency,

        PaymentMethod method,

        /** Defaults to now() when null. */
        Instant paidAt,

        @Size(max = 500)
        String description
) {}
