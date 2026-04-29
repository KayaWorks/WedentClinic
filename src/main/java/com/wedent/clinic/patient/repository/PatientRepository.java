package com.wedent.clinic.patient.repository;

import com.wedent.clinic.patient.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    @EntityGraph(attributePaths = {"clinic", "company"})
    Optional<Patient> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndPhone(Long companyId, String phone);

    boolean existsByCompanyIdAndPhoneAndIdNot(Long companyId, String phone, Long id);

    @Query("""
            SELECT p FROM Patient p
            WHERE p.company.id = :companyId
              AND (:clinicId IS NULL OR p.clinic.id = :clinicId)
              AND (:name IS NULL OR
                   LOWER(p.firstName) LIKE LOWER(CONCAT('%', :name, '%')) OR
                   LOWER(p.lastName)  LIKE LOWER(CONCAT('%', :name, '%')) OR
                   LOWER(CONCAT(p.firstName, ' ', p.lastName)) LIKE LOWER(CONCAT('%', :name, '%')))
              AND (:phone IS NULL OR p.phone LIKE CONCAT('%', :phone, '%'))
            """)
    Page<Patient> search(Long companyId, Long clinicId, String name, String phone, Pageable pageable);

    /**
     * Lightweight count for dashboard / "/api/patients/count" without the
     * list materialization cost of {@link #search}.
     */
    @Query("""
            SELECT COUNT(p) FROM Patient p
            WHERE p.company.id = :companyId
              AND (:clinicId IS NULL OR p.clinic.id = :clinicId)
            """)
    long countByScope(Long companyId, Long clinicId);
}
