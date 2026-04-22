package com.wedent.clinic.employee.controller;

import com.wedent.clinic.common.dto.ApiResponse;
import com.wedent.clinic.employee.dto.DoctorProfileRequest;
import com.wedent.clinic.employee.dto.DoctorProfileResponse;
import com.wedent.clinic.employee.service.DoctorProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Doctor Profiles")
@RestController
@RequestMapping("/api/employees/{employeeId}/doctor-profile")
@RequiredArgsConstructor
public class DoctorProfileController {

    private final DoctorProfileService doctorProfileService;

    @Operation(summary = "Get doctor profile for an employee")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR')")
    @GetMapping
    public ResponseEntity<ApiResponse<DoctorProfileResponse>> get(@PathVariable Long employeeId) {
        return ResponseEntity.ok(ApiResponse.ok(doctorProfileService.getByEmployeeId(employeeId)));
    }

    @Operation(summary = "Create or update doctor profile (commission/salary)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @PutMapping
    public ResponseEntity<ApiResponse<DoctorProfileResponse>> upsert(
            @PathVariable Long employeeId,
            @Valid @RequestBody DoctorProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(doctorProfileService.upsert(employeeId, request)));
    }
}
