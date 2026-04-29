package com.wedent.clinic.file.controller;

import com.wedent.clinic.common.dto.ApiResponse;
import com.wedent.clinic.file.dto.PatientFileResponse;
import com.wedent.clinic.file.entity.PatientFileCategory;
import com.wedent.clinic.file.service.PatientFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Patient-scoped file upload / download / delete.
 *
 * <p>All routes live under {@code /api/patients/{patientId}/files}.
 * Tenant + clinic scope is enforced in the service layer; the controller
 * only handles HTTP concerns (multipart parsing, binary streaming, headers).</p>
 */
@Tag(name = "Patient Files")
@RestController
@RequestMapping("/api/patients/{patientId}/files")
@RequiredArgsConstructor
public class PatientFileController {

    private final PatientFileService patientFileService;

    @Operation(summary = "Upload a file for a patient")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PatientFileResponse>> upload(
            @PathVariable Long patientId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "OTHER") PatientFileCategory category,
            @RequestParam(value = "description", required = false) String description) {
        PatientFileResponse response = patientFileService.upload(patientId, file, category, description);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @Operation(summary = "List file metadata for a patient (no binary content)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PatientFileResponse>>> list(
            @PathVariable Long patientId) {
        return ResponseEntity.ok(ApiResponse.ok(patientFileService.listForPatient(patientId)));
    }

    @Operation(summary = "Download the binary content of a file")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping("/{fileId}/content")
    public ResponseEntity<byte[]> download(
            @PathVariable Long patientId,
            @PathVariable Long fileId) {
        PatientFileService.DownloadResult result = patientFileService.download(fileId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(result.meta().mimeType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(result.meta().fileName())
                .build());
        headers.setContentLength(result.content().length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(result.content());
    }

    @Operation(summary = "Soft-delete a patient file")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @DeleteMapping("/{fileId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long patientId,
            @PathVariable Long fileId) {
        patientFileService.delete(fileId);
        return ResponseEntity.ok(ApiResponse.ok("Dosya silindi"));
    }
}
