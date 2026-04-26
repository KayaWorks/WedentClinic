package com.wedent.clinic.payment.dto;

import java.math.BigDecimal;

/**
 * Read-only patient financial summary.
 *
 * <p>{@code balance = totalPaid - totalFees}. Positive → patient has credit;
 * negative → patient owes the clinic.</p>
 */
public record PatientBalanceResponse(
        Long patientId,
        /** Sum of all COMPLETED payments. */
        BigDecimal totalPaid,
        /** Sum of all active treatment fees (regardless of treatment status). */
        BigDecimal totalFees,
        /** totalPaid - totalFees */
        BigDecimal balance,
        String currency
) {}
