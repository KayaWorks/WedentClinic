package com.wedent.clinic.payout.dto;

import com.wedent.clinic.payout.entity.PayoutStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Full detail view. For list endpoints the treatments + deductions
 * arrays may be empty — they're only populated on the detail path.
 */
public record PayoutResponse(
        Long id,
        Long companyId,
        Long clinicId,
        Long doctorProfileId,
        Long doctorEmployeeId,
        String doctorName,
        LocalDate periodStart,
        LocalDate periodEnd,
        PayoutStatus status,
        BigDecimal treatmentTotalSnapshot,
        BigDecimal commissionRateSnapshot,
        BigDecimal grossAmount,
        BigDecimal totalDeduction,
        BigDecimal netAmount,
        Instant approvedAt,
        Instant paidAt,
        Instant createdAt,
        Instant updatedAt,
        List<PayoutDeductionResponse> deductions,
        List<PayoutTreatmentLine> treatments
) {}
