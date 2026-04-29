package com.wedent.clinic.file.service;

import com.wedent.clinic.file.dto.PatientFileResponse;
import com.wedent.clinic.file.entity.PatientFileCategory;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PatientFileService {

    /**
     * Persists a new file attachment for the given patient.
     *
     * @param patientId   patient that owns the file
     * @param file        uploaded multipart data
     * @param category    clinical category (e.g. PANORAMIC_XRAY)
     * @param description optional human-readable note
     * @return metadata of the newly created record (no content bytes)
     */
    PatientFileResponse upload(Long patientId, MultipartFile file,
                               PatientFileCategory category, String description);

    /**
     * Returns metadata for all active files belonging to the patient.
     * Binary content is never included in the response.
     */
    List<PatientFileResponse> listForPatient(Long patientId);

    /**
     * Returns metadata + binary bytes for the requested file.
     * Callers must treat the byte array as a read-only view.
     */
    DownloadResult download(Long fileId);

    /** Soft-deletes the file (sets active = false). */
    void delete(Long fileId);

    /** Carries file metadata together with its binary content for the download path. */
    record DownloadResult(PatientFileResponse meta, byte[] content) {}
}
