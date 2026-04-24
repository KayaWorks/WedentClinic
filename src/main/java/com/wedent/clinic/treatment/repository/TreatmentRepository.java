package com.wedent.clinic.treatment.repository;

import com.wedent.clinic.treatment.entity.Treatment;
import com.wedent.clinic.treatment.entity.TreatmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TreatmentRepository extends JpaRepository<Treatment, Long> {

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
     * Payout aggregation. Only completed treatments in the half-open
     * {@code [from, to)} window count toward the doctor's gross — planned
     * and cancelled rows stay out.
     */
    @Query("""
            SELECT t FROM Treatment t
            WHERE t.doctor.id = :doctorId
              AND t.status = :status
              AND t.performedAt >= :from
              AND t.performedAt < :to
            ORDER BY t.performedAt ASC
            """)
    List<Treatment> findCompletedForDoctorInRange(Long doctorId,
                                                  TreatmentStatus status,
                                                  Instant from,
                                                  Instant to);
}
