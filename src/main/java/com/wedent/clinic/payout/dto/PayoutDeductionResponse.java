package com.wedent.clinic.payout.dto;

import com.wedent.clinic.payout.entity.PayoutDeductionType;

import java.math.BigDecimal;

public record PayoutDeductionResponse(
        Long id,
        PayoutDeductionType type,
        String description,
        BigDecimal amount
) {}
