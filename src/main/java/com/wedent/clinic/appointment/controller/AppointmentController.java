package com.wedent.clinic.appointment.controller;

import com.wedent.clinic.appointment.dto.AppointmentCreateRequest;
import com.wedent.clinic.appointment.dto.AppointmentResponse;
import com.wedent.clinic.appointment.dto.AppointmentStatusUpdateRequest;
import com.wedent.clinic.appointment.dto.AppointmentUpdateRequest;
import com.wedent.clinic.appointment.entity.AppointmentStatus;
import com.wedent.clinic.appointment.service.AppointmentService;
import com.wedent.clinic.common.dto.ApiResponse;
import com.wedent.clinic.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Appointments")
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @Operation(summary = "Create appointment (validates conflict for doctor & time)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @PostMapping
    public ResponseEntity<ApiResponse<AppointmentResponse>> create(
            @Valid @RequestBody AppointmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(appointmentService.create(request)));
    }

    @Operation(summary = "List/search appointments by filters")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AppointmentResponse>>> list(
            @RequestParam(required = false) Long clinicId,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) AppointmentStatus status,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                appointmentService.search(clinicId, doctorId, patientId, date, status, pageable)));
    }

    @Operation(summary = "Get appointment by id")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.getById(id)));
    }

    @Operation(summary = "Update appointment slot/note (re-validates conflict)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody AppointmentUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.update(id, request)));
    }

    @Operation(summary = "Change appointment status (state-machine validated)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AppointmentResponse>> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody AppointmentStatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.changeStatus(id, request)));
    }

    @Operation(summary = "Soft-delete appointment")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        appointmentService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Appointment deleted"));
    }
}
