package com.wedent.clinic.file.repository;

import com.wedent.clinic.file.dto.PatientFileResponse;
import com.wedent.clinic.file.entity.PatientFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PatientFileRepository extends JpaRepository<PatientFile, Long> {

    /**
     * Returns metadata (no content bytes) for all active files of a patient.
     * The JPQL constructor expression guarantees the {@code content} column is
     * never selected, keeping list responses lightweight.
     */
    @Query("""
            SELECT new com.wedent.clinic.file.dto.PatientFileResponse(
                f.id, f.patient.id, f.clinic.id, f.company.id,
                f.uploadedBy.id, f.category, f.fileName, f.mimeType,
                f.fileSizeBytes, f.description, f.createdAt
            )
            FROM PatientFile f
            WHERE f.patient.id = :patientId AND f.company.id = :companyId
            ORDER BY f.createdAt DESC
            """)
    List<PatientFileResponse> findMetaByPatientIdAndCompanyId(
            @Param("patientId") Long patientId,
            @Param("companyId") Long companyId);

    /**
     * Returns a single file's metadata by id (used before download / delete
     * to verify tenant scope and clinic access).
     */
    @Query("""
            SELECT new com.wedent.clinic.file.dto.PatientFileResponse(
                f.id, f.patient.id, f.clinic.id, f.company.id,
                f.uploadedBy.id, f.category, f.fileName, f.mimeType,
                f.fileSizeBytes, f.description, f.createdAt
            )
            FROM PatientFile f
            WHERE f.id = :id AND f.company.id = :companyId
            """)
    Optional<PatientFileResponse> findMetaByIdAndCompanyId(
            @Param("id") Long id,
            @Param("companyId") Long companyId);

    /**
     * Fetches only the raw bytes for a file, scoped to the caller's company.
     * Called exclusively by the download endpoint after the access check.
     */
    @Query("SELECT f.content FROM PatientFile f WHERE f.id = :id AND f.company.id = :companyId")
    Optional<byte[]> findContentByIdAndCompanyId(
            @Param("id") Long id,
            @Param("companyId") Long companyId);
}
