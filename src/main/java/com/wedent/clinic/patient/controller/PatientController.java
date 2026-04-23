package com.wedent.clinic.patient.controller;

import com.wedent.clinic.common.dto.ApiResponse;
import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.patient.dto.PatientCountResponse;
import com.wedent.clinic.patient.dto.PatientCreateRequest;
import com.wedent.clinic.patient.dto.PatientResponse;
import com.wedent.clinic.patient.dto.PatientUpdateRequest;
import com.wedent.clinic.patient.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Patients")
@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @Operation(summary = "Create a new patient")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @PostMapping
    public ResponseEntity<ApiResponse<PatientResponse>> create(@Valid @RequestBody PatientCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(patientService.create(request)));
    }

    @Operation(summary = "Search patients by name or phone (within company/clinic scope)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PatientResponse>>> search(
            @RequestParam(required = false) Long clinicId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(patientService.search(clinicId, name, phone, pageable)));
    }

    @Operation(summary = "Scope-aware patient count (company-wide for owners, clinic-clamped otherwise)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<PatientCountResponse>> count(
            @RequestParam(required = false) Long clinicId) {
        long total = patientService.count(clinicId);
        return ResponseEntity.ok(ApiResponse.ok(new PatientCountResponse(clinicId, total)));
    }

    @Operation(summary = "Fetch a patient by id")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PatientResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(patientService.getById(id)));
    }

    @Operation(summary = "Update an existing patient")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PatientResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody PatientUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(patientService.update(id, request)));
    }

    @Operation(summary = "Soft-delete a patient")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        patientService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Patient deleted"));
    }
}
