package com.wedent.clinic.payout.service;

import com.wedent.clinic.treatment.entity.Treatment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Pure calculation helper — given a list of eligible treatments,
 * a commission rate (0-100, as stored on {@code DoctorProfile}),
 * and total deductions, produces gross/net amounts.
 *
 * <p>Kept deliberately stateless so it's trivial to unit-test: no
 * repository access, no security context, no side effects.</p>
 */
@Slf4j
@Component
public class PayoutCalculator {

    /** Scale all computed money fields at this precision. */
    private static final int MONEY_SCALE = 2;

    /**
     * Percentage denominator — {@code DoctorProfile.commissionRate} is
     * stored as 0-100 (e.g. {@code 25.00} for 25%) so we divide by 100
     * to get the multiplier.
     */
    private static final BigDecimal PERCENT = new BigDecimal("100");

    public Result compute(List<Treatment> eligibleTreatments,
                          BigDecimal commissionRate,
                          BigDecimal totalDeduction) {

        BigDecimal treatmentTotal = BigDecimal.ZERO;
        for (Treatment t : eligibleTreatments) {
            if (t.getFee() != null) {
                treatmentTotal = treatmentTotal.add(t.getFee());
            }
        }
        treatmentTotal = treatmentTotal.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal safeRate = commissionRate == null ? BigDecimal.ZERO : commissionRate;
        BigDecimal gross = treatmentTotal
                .multiply(safeRate)
                .divide(PERCENT, MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal safeDed = totalDeduction == null ? BigDecimal.ZERO : totalDeduction;
        BigDecimal net = gross.subtract(safeDed).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        return new Result(treatmentTotal, gross, safeDed.setScale(MONEY_SCALE, RoundingMode.HALF_UP), net);
    }

    /**
     * Immutable result tuple — keeps the caller from accidentally
     * reading gross/net back off the source objects (which are still
     * live while the DRAFT is being built).
     */
    public record Result(
            BigDecimal treatmentTotal,
            BigDecimal grossAmount,
            BigDecimal totalDeduction,
            BigDecimal netAmount
    ) {}
}
