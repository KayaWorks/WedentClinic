package com.wedent.clinic.treatment.entity;

import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.common.entity.BaseEntity;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.employee.entity.Employee;
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
 * One performed (or planned) procedure on a patient. The doctor reference
 * doubles as the payout-target: every {@link TreatmentStatus#COMPLETED}
 * treatment in a payout period is summed against the doctor's
 * {@code DoctorProfile.commissionRate} to produce gross hakediş.
 *
 * <p>{@link #payoutLockedAt} is set by the payouts module the moment a
 * treatment is consumed by an issued payout — after that, the service
 * refuses edits/deletes so already-issued numbers can't drift.</p>
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
@Table(name = "treatments")
@SQLDelete(sql = "UPDATE treatments SET active = false, updated_at = NOW() WHERE id = ?")
@SQLRestriction("active = true")
public class Treatment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Employee doctor;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Optional FDI/Universal tooth notation. Free-form to dodge a numbering-system fight. */
    @Column(name = "tooth_number", length = 8)
    private String toothNumber;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    @Column(name = "fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal fee;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TreatmentStatus status;

    /**
     * Set by the payouts module when this treatment is consumed by an
     * issued payout. {@code null} → free to edit/delete.
     */
    @Column(name = "payout_locked_at")
    private Instant payoutLockedAt;

    public boolean isPayoutLocked() {
        return payoutLockedAt != null;
    }
}
