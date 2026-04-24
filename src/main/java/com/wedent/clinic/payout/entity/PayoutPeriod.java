package com.wedent.clinic.payout.entity;

import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.common.entity.BaseEntity;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.employee.entity.DoctorProfile;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A single doctor-payout run over a half-open date window
 * {@code [periodStart, periodEnd)}.
 *
 * <p>{@link #treatmentTotalSnapshot} and {@link #commissionRateSnapshot}
 * are populated at {@link PayoutStatus#APPROVED APPROVE} time — after
 * that the numbers printed on the payout report are immutable by
 * contract, even if the doctor's {@code commissionRate} is edited or
 * one of the source treatments is later adjusted (which, separately,
 * is blocked by {@code treatment.payoutLockedAt}).</p>
 *
 * <p>Soft-delete is intentionally <em>not</em> applied here: a cancelled
 * payout keeps {@code active = true} with {@code status = CANCELLED} so
 * the audit trail stays queryable. If we ever need true deletion it
 * should be a separate explicit admin flow.</p>
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
@Table(name = "payout_periods")
public class PayoutPeriod extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_profile_id", nullable = false)
    private DoctorProfile doctorProfile;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    /** Exclusive upper bound. */
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutStatus status;

    /** Sum of included treatment fees at approval time — frozen. */
    @Column(name = "treatment_total_snapshot", precision = 14, scale = 2)
    private BigDecimal treatmentTotalSnapshot;

    /** Commission rate copied off DoctorProfile at approval — frozen. */
    @Column(name = "commission_rate_snapshot", precision = 5, scale = 2)
    private BigDecimal commissionRateSnapshot;

    @Column(name = "gross_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "total_deduction", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDeduction;

    @Column(name = "net_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @OneToMany(
            mappedBy = "payoutPeriod",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<PayoutDeduction> deductions = new ArrayList<>();

    public boolean isDraft()     { return status == PayoutStatus.DRAFT; }
    public boolean isApproved()  { return status == PayoutStatus.APPROVED; }
    public boolean isPaid()      { return status == PayoutStatus.PAID; }
    public boolean isCancelled() { return status == PayoutStatus.CANCELLED; }
    public boolean isImmutable() { return status != PayoutStatus.DRAFT; }
}
