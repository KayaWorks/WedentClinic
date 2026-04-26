package com.wedent.clinic.note.controller;

import com.wedent.clinic.common.dto.ApiResponse;
import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.note.dto.PatientNoteCreateRequest;
import com.wedent.clinic.note.dto.PatientNoteResponse;
import com.wedent.clinic.note.dto.PatientNoteUpdateRequest;
import com.wedent.clinic.note.service.PatientNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Patient-scoped writes (create + list) live under
 * {@code /api/patients/{patientId}/notes}; once a note exists, mutations
 * (update / delete) go through the flat {@code /api/patient-notes/{id}} surface
 * so the FE can operate without first re-resolving the patient id.
 *
 * <p>All four roles can read notes. Creates/updates/deletes are open to any
 * authenticated clinic member — the service layer enforces clinic-scope.</p>
 */
@Tag(name = "Patient Notes")
@RestController
@RequiredArgsConstructor
public class PatientNoteController {

    private final PatientNoteService patientNoteService;

    // ─── Patient-scoped routes ──────────────────────────────────────────────

    @Operation(summary = "Create a note for the given patient")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @PostMapping("/api/patients/{patientId}/notes")
    public ResponseEntity<ApiResponse<PatientNoteResponse>> create(
            @PathVariable Long patientId,
            @Valid @RequestBody PatientNoteCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(patientNoteService.create(patientId, request)));
    }

    @Operation(summary = "List notes for the given patient (pinned first, then newest)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping("/api/patients/{patientId}/notes")
    public ResponseEntity<ApiResponse<PageResponse<PatientNoteResponse>>> list(
            @PathVariable Long patientId,
            @ParameterObject @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                patientNoteService.listForPatient(patientId, pageable)));
    }

    // ─── Flat by-id routes ─────────────────────────────────────────────────

    @Operation(summary = "Patch a note (title, content, type, pinned)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @PatchMapping("/api/patient-notes/{id}")
    public ResponseEntity<ApiResponse<PatientNoteResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody PatientNoteUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(patientNoteService.update(id, request)));
    }

    @Operation(summary = "Soft-delete a note")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @DeleteMapping("/api/patient-notes/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        patientNoteService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
