package com.wedent.clinic.payout;

import com.wedent.clinic.payout.service.PayoutCalculator;
import com.wedent.clinic.treatment.entity.Treatment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PayoutCalculatorTest {

    private final PayoutCalculator calculator = new PayoutCalculator();

    @Test
    void compute_zeroTreatments_zeroGrossAndNet() {
        PayoutCalculator.Result r = calculator.compute(
                List.of(), new BigDecimal("25.00"), BigDecimal.ZERO);
        assertThat(r.treatmentTotal()).isEqualByComparingTo("0.00");
        assertThat(r.grossAmount()).isEqualByComparingTo("0.00");
        assertThat(r.netAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void compute_treatmentsWith25PctRate_grossIsQuarterOfTotal() {
        List<Treatment> ts = List.of(
                treatment("500.00"),
                treatment("1500.00"),
                treatment("2000.00")); // total 4000
        PayoutCalculator.Result r = calculator.compute(
                ts, new BigDecimal("25.00"), BigDecimal.ZERO);
        assertThat(r.treatmentTotal()).isEqualByComparingTo("4000.00");
        assertThat(r.grossAmount()).isEqualByComparingTo("1000.00");
        assertThat(r.netAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void compute_deductionsSubtractedFromGross_notTreatmentTotal() {
        List<Treatment> ts = List.of(treatment("1000.00"));
        PayoutCalculator.Result r = calculator.compute(
                ts, new BigDecimal("50.00"), new BigDecimal("200.00"));
        // gross = 1000 * 50% = 500; net = 500 - 200 = 300
        assertThat(r.grossAmount()).isEqualByComparingTo("500.00");
        assertThat(r.netAmount()).isEqualByComparingTo("300.00");
        assertThat(r.totalDeduction()).isEqualByComparingTo("200.00");
    }

    @Test
    void compute_deductionsExceedGross_netGoesNegative() {
        List<Treatment> ts = List.of(treatment("100.00"));
        PayoutCalculator.Result r = calculator.compute(
                ts, new BigDecimal("10.00"), new BigDecimal("50.00"));
        // gross = 10.00; net = 10 - 50 = -40
        assertThat(r.grossAmount()).isEqualByComparingTo("10.00");
        assertThat(r.netAmount()).isEqualByComparingTo("-40.00");
    }

    @Test
    void compute_nullCommissionRate_treatedAsZero_grossIsZero() {
        List<Treatment> ts = List.of(treatment("1000.00"));
        PayoutCalculator.Result r = calculator.compute(ts, null, BigDecimal.ZERO);
        assertThat(r.grossAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void compute_halfPercentRate_roundingHalfUp() {
        // 333.33 × 33.33% = 111.100989; rounded HALF_UP to scale=2 → 111.10
        List<Treatment> ts = List.of(treatment("333.33"));
        PayoutCalculator.Result r = calculator.compute(
                ts, new BigDecimal("33.33"), BigDecimal.ZERO);
        assertThat(r.grossAmount()).isEqualByComparingTo("111.10");
    }

    @Test
    void compute_treatmentWithNullFee_ignored() {
        List<Treatment> ts = List.of(treatment("500.00"), treatment(null));
        PayoutCalculator.Result r = calculator.compute(
                ts, new BigDecimal("20.00"), BigDecimal.ZERO);
        assertThat(r.treatmentTotal()).isEqualByComparingTo("500.00");
        assertThat(r.grossAmount()).isEqualByComparingTo("100.00");
    }

    private static Treatment treatment(String fee) {
        return Treatment.builder()
                .fee(fee == null ? null : new BigDecimal(fee))
                .build();
    }
}
