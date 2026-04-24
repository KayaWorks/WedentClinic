package com.wedent.clinic.payout.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Materialize a fresh DRAFT payout for {@code doctorProfileId} over the
 * half-open window {@code [periodStart, periodEnd)}. The service runs
 * the calculator immediately so the returned row has live gross/net.
 */
public record PayoutDraftRequest(
        @NotNull Long doctorProfileId,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd
) {}
