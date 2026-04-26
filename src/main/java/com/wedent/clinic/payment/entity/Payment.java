package com.wedent.clinic.payment.entity;

import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.common.entity.BaseEntity;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.patient.entity.Patient;
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
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A monetary payment recorded for a patient. The patient's balance is the
 * sum of COMPLETED payments minus the sum of all treatment fees.
 *
 * <p>Once a payment is persisted in {@link PaymentStatus#COMPLETED} it can
 * only move to {@link PaymentStatus#CANCELLED} — amounts are never edited
 * in-place to preserve the audit trail. Corrections are done by cancelling
 * the wrong entry and recording a new one.</p>
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
@Table(name = "payments")
@SQLDelete(sql = "UPDATE payments SET active = false, updated_at = NOW() WHERE id = ?")
@SQLRestriction("active = true")
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 30)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    /** When the money was actually received / transferred. */
    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @Column(name = "description", length = 500)
    private String description;

    /** Set when status transitions to CANCELLED. */
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;
}
