package com.wedent.clinic.clinic.controller;

import com.wedent.clinic.clinic.dto.ClinicCreateRequest;
import com.wedent.clinic.clinic.dto.ClinicResponse;
import com.wedent.clinic.clinic.dto.ClinicUpdateRequest;
import com.wedent.clinic.clinic.service.ClinicService;
import com.wedent.clinic.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reads are available to every authenticated role (tenant-scoped at service);
 * writes are owner-only. The frontend "Yeni Klinik" flow talks to
 * {@code POST /api/clinics}; the Klinikler sekmesi uses {@code PATCH /{id}}
 * for inline edits. PUT is also exposed for clients that prefer full-rewrite
 * semantics — the body shape is identical so it maps to the same handler.
 */
@Tag(name = "Clinics")
@RestController
@RequestMapping("/api/clinics")
@RequiredArgsConstructor
public class ClinicController {

    private final ClinicService clinicService;

    @Operation(summary = "List clinics (owner: all under company, others: own clinic only)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ClinicResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(clinicService.list()));
    }

    @Operation(summary = "Fetch a single clinic by id (tenant-scoped)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ClinicResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(clinicService.getById(id)));
    }

    @Operation(summary = "Create a new clinic under the caller's company (owner-only)")
    @PreAuthorize("hasRole('CLINIC_OWNER')")
    @PostMapping
    public ResponseEntity<ApiResponse<ClinicResponse>> create(@Valid @RequestBody ClinicCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(clinicService.create(request)));
    }

    @Operation(summary = "Partially update a clinic (owner-only)")
    @PreAuthorize("hasRole('CLINIC_OWNER')")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ClinicResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody ClinicUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(clinicService.update(id, request)));
    }

    @Operation(summary = "Replace editable fields of a clinic (owner-only)")
    @PreAuthorize("hasRole('CLINIC_OWNER')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ClinicResponse>> replace(
            @PathVariable Long id,
            @Valid @RequestBody ClinicUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(clinicService.update(id, request)));
    }

    @Operation(summary = "Soft-delete a clinic (owner-only, not the last active one)")
    @PreAuthorize("hasRole('CLINIC_OWNER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        clinicService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Clinic deleted"));
    }
}
