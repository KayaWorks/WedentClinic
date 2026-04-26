package com.wedent.clinic.payment.dto;

import com.wedent.clinic.payment.entity.PaymentMethod;
import com.wedent.clinic.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long patientId,
        Long clinicId,
        Long companyId,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        PaymentStatus status,
        Instant paidAt,
        String description,
        Instant cancelledAt,
        String cancelReason,
        Instant createdAt,
        Instant updatedAt
) {}
