package com.wedent.clinic.payment.repository;

import com.wedent.clinic.payment.entity.Payment;
import com.wedent.clinic.payment.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Page<Payment> findByPatientIdAndCompanyId(Long patientId, Long companyId, Pageable pageable);

    Optional<Payment> findByIdAndCompanyId(Long id, Long companyId);

    /**
     * Sum of COMPLETED payment amounts for one patient.
     * Returns {@code null} when there are no rows — callers coerce to ZERO.
     */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM Payment p
            WHERE p.patient.id = :patientId
              AND p.company.id = :companyId
              AND p.status = :status
            """)
    BigDecimal sumByPatientIdAndCompanyIdAndStatus(Long patientId, Long companyId, PaymentStatus status);

    /**
     * Sum of all active treatment fees for one patient (used for balance).
     * Lives here so balance is a single-service concern.
     */
    @Query("""
            SELECT COALESCE(SUM(t.fee), 0)
            FROM Treatment t
            WHERE t.patient.id = :patientId
              AND t.company.id = :companyId
            """)
    BigDecimal sumTreatmentFeeByPatientIdAndCompanyId(Long patientId, Long companyId);
}
