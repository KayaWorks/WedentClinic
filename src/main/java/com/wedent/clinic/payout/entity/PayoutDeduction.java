package com.wedent.clinic.payout.entity;

import com.wedent.clinic.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One line-item subtracted from a payout's gross amount — lab fee,
 * material expense, advance payment clawback, penalty, etc.
 *
 * <p>Add/remove is only permitted while the parent period is
 * {@link PayoutStatus#DRAFT DRAFT}. Once APPROVED these rows are
 * effectively archival.</p>
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "payout_deductions")
public class PayoutDeduction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payout_period_id", nullable = false)
    private PayoutPeriod payoutPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private PayoutDeductionType type;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
}
