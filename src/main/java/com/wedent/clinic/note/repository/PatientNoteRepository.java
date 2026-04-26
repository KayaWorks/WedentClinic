package com.wedent.clinic.note.repository;

import com.wedent.clinic.note.entity.PatientNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PatientNoteRepository extends JpaRepository<PatientNote, Long> {

    /**
     * Pinned notes first, then newest first. Both filters applied at DB level
     * to avoid loading inactive rows through the @SQLRestriction bypass paths.
     */
    @Query("""
            SELECT n FROM PatientNote n
            WHERE n.patient.id = :patientId
              AND n.company.id = :companyId
            ORDER BY n.pinned DESC, n.createdAt DESC
            """)
    Page<PatientNote> findByPatientIdAndCompanyId(Long patientId, Long companyId, Pageable pageable);

    Optional<PatientNote> findByIdAndCompanyId(Long id, Long companyId);
}
