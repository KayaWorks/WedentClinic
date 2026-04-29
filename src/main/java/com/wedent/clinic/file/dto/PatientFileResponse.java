package com.wedent.clinic.file.dto;

import com.wedent.clinic.file.entity.PatientFileCategory;

import java.time.Instant;

/**
 * Metadata-only DTO returned by list and upload endpoints.
 * Binary content is never included — callers use the download endpoint.
 *
 * <p>The canonical constructor order must match the JPQL {@code new} expression
 * in {@link com.wedent.clinic.file.repository.PatientFileRepository}.</p>
 */
public record PatientFileResponse(
        Long id,
        Long patientId,
        Long clinicId,
        Long companyId,
        Long uploadedByUserId,
        PatientFileCategory category,
        String fileName,
        String mimeType,
        Long fileSizeBytes,
        String description,
        Instant createdAt
) {}
