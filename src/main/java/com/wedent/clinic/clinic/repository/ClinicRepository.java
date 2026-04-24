package com.wedent.clinic.clinic.repository;

import com.wedent.clinic.clinic.entity.Clinic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClinicRepository extends JpaRepository<Clinic, Long> {

    List<Clinic> findAllByCompanyId(Long companyId);

    Optional<Clinic> findByIdAndCompanyId(Long id, Long companyId);

    /** Case-insensitive uniqueness guard for create (no DB constraint — app-level). */
    boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);

    /** Same guard for update — excludes the row being edited. */
    boolean existsByCompanyIdAndNameIgnoreCaseAndIdNot(Long companyId, String name, Long id);
}
