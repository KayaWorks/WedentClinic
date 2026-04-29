package com.wedent.clinic.treatment.controller;

import com.wedent.clinic.common.dto.ApiResponse;
import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.treatment.dto.TreatmentCreateRequest;
import com.wedent.clinic.treatment.dto.TreatmentResponse;
import com.wedent.clinic.treatment.dto.TreatmentUpdateRequest;
import com.wedent.clinic.treatment.service.TreatmentService;
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
 * {@code /api/patients/{patientId}/treatments}; once a treatment exists,
 * cross-patient navigation (read / update / delete) goes through the
 * flat {@code /api/treatments/{id}} surface so the FE can deep-link
 * without first re-resolving the patient.
 *
 * <p>Read access mirrors the patient module (all four roles); writes
 * exclude {@code STAFF} since fee/status changes feed payouts.</p>
 */
@Tag(name = "Treatments")
@RestController
@RequiredArgsConstructor
public class TreatmentController {

    private final TreatmentService treatmentService;

    // ─── Patient-scoped routes ──────────────────────────────────────────────

    @Operation(summary = "Create a treatment for the given patient")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR')")
    @PostMapping("/api/patients/{patientId}/treatments")
    public ResponseEntity<ApiResponse<TreatmentResponse>> create(
            @PathVariable Long patientId,
            @Valid @RequestBody TreatmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(treatmentService.create(patientId, request)));
    }

    @Operation(summary = "List treatments for the given patient (newest first by default)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping("/api/patients/{patientId}/treatments")
    public ResponseEntity<ApiResponse<PageResponse<TreatmentResponse>>> list(
            @PathVariable Long patientId,
            @ParameterObject @PageableDefault(size = 20, sort = "performedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                treatmentService.listForPatient(patientId, pageable)));
    }

    // ─── Flat by-id routes ─────────────────────────────────────────────────

    @Operation(summary = "Fetch a single treatment by id")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping("/api/treatments/{id}")
    public ResponseEntity<ApiResponse<TreatmentResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(treatmentService.getById(id)));
    }

    @Operation(summary = "Patch a treatment (blocked once payout-locked)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR')")
    @PatchMapping("/api/treatments/{id}")
    public ResponseEntity<ApiResponse<TreatmentResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody TreatmentUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(treatmentService.update(id, request)));
    }

    @Operation(summary = "Soft-delete a treatment (blocked once payout-locked)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @DeleteMapping("/api/treatments/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        treatmentService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Treatment deleted"));
    }
}
