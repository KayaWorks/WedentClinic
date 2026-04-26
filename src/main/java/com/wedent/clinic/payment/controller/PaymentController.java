package com.wedent.clinic.payment.controller;

import com.wedent.clinic.common.dto.ApiResponse;
import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.payment.dto.PatientBalanceResponse;
import com.wedent.clinic.payment.dto.PaymentCancelRequest;
import com.wedent.clinic.payment.dto.PaymentCreateRequest;
import com.wedent.clinic.payment.dto.PaymentResponse;
import com.wedent.clinic.payment.service.PaymentService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Patient-scoped creates + list under {@code /api/patients/{patientId}/payments}.
 * Individual payment operations go through the flat {@code /api/payments/{id}} surface.
 * Balance aggregation at {@code /api/patients/{patientId}/balance}.
 *
 * <p>Writes (create, cancel) require at least MANAGER role; reads are open to all roles.</p>
 */
@Tag(name = "Payments")
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // ─── Patient-scoped routes ──────────────────────────────────────────────

    @Operation(summary = "Record a payment for the given patient")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @PostMapping("/api/patients/{patientId}/payments")
    public ResponseEntity<ApiResponse<PaymentResponse>> create(
            @PathVariable Long patientId,
            @Valid @RequestBody PaymentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(paymentService.create(patientId, request)));
    }

    @Operation(summary = "List payments for the given patient (newest first)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping("/api/patients/{patientId}/payments")
    public ResponseEntity<ApiResponse<PageResponse<PaymentResponse>>> list(
            @PathVariable Long patientId,
            @ParameterObject @PageableDefault(size = 20, sort = "paidAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                paymentService.listForPatient(patientId, pageable)));
    }

    @Operation(summary = "Get the financial balance for the given patient")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping("/api/patients/{patientId}/balance")
    public ResponseEntity<ApiResponse<PatientBalanceResponse>> balance(
            @PathVariable Long patientId) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getBalance(patientId)));
    }

    // ─── Flat by-id routes ─────────────────────────────────────────────────

    @Operation(summary = "Fetch a single payment by id")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping("/api/payments/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getById(id)));
    }

    @Operation(summary = "Cancel a payment")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @PostMapping("/api/payments/{id}/cancel")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancel(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) PaymentCancelRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.cancel(id, request)));
    }
}
