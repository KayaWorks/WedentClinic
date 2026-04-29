package com.wedent.clinic.treatment.repository;

import com.wedent.clinic.treatment.entity.Treatment;
import com.wedent.clinic.treatment.entity.TreatmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TreatmentRepository extends JpaRepository<Treatment, Long> {

    // ── Aggregate helpers for patient summary ──────────────────────────────

    long countByPatientIdAndCompanyIdAndStatus(Long patientId, Long companyId, TreatmentStatus status);

    @Query("""
            SELECT COALESCE(SUM(t.fee), 0)
            FROM Treatment t
            WHERE t.patient.id = :patientId
              AND t.company.id = :companyId
              AND t.status  = :status
            """)
    BigDecimal sumFeeByPatientIdAndCompanyIdAndStatus(Long patientId, Long companyId, TreatmentStatus status);

    /**
     * Tenant-scoped fetch with relations eagerly joined — used by every
     * single-row endpoint so the response mapper doesn't trigger lazy hits.
     */
    @EntityGraph(attributePaths = {"company", "clinic", "patient", "doctor"})
    Optional<Treatment> findByIdAndCompanyId(Long id, Long companyId);

    /**
     * Patient-profile timeline. Newest first is enforced by the {@code Pageable}
     * sort the controller passes in; the index
     * {@code idx_treatments_patient_performed} backs the default order.
     */
    @EntityGraph(attributePaths = {"doctor"})
    Page<Treatment> findByPatientIdAndCompanyId(Long patientId, Long companyId, Pageable pageable);

    /**
     * Payout aggregation. Only completed, still-unlocked treatments in
     * the half-open {@code [from, to)} window count toward gross. Keying
     * off {@code completedAt} (not {@code performedAt}) lets billing
     * happen on a different day than the chair work.
     */
    @Query("""
            SELECT t FROM Treatment t
            WHERE t.doctor.id = :doctorId
              AND t.company.id = :companyId
              AND t.status = :status
              AND t.payoutLockedAt IS NULL
              AND t.completedAt >= :from
              AND t.completedAt < :to
            ORDER BY t.completedAt ASC
            """)
    List<Treatment> findEligibleForPayout(Long doctorId,
                                          Long companyId,
                                          TreatmentStatus status,
                                          Instant from,
                                          Instant to);

    /**
     * Treatments that were consumed by a specific approved/paid payout
     * period. Used by the payout detail endpoint to show "this is what
     * we paid for". Patient is eagerly joined so the response mapper
     * can render patient name without n+1 round-trips.
     */
    @EntityGraph(attributePaths = {"patient"})
    List<Treatment> findByPayoutPeriodId(Long payoutPeriodId);
}
