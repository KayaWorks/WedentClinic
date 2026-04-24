package com.wedent.clinic.payout.controller;

import com.wedent.clinic.common.dto.ApiResponse;
import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.payout.dto.PayoutDeductionRequest;
import com.wedent.clinic.payout.dto.PayoutDraftRequest;
import com.wedent.clinic.payout.dto.PayoutResponse;
import com.wedent.clinic.payout.entity.PayoutStatus;
import com.wedent.clinic.payout.service.PayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Hakediş (payout) endpoints.
 *
 * <p><b>RBAC:</b> CLINIC_OWNER + MANAGER manage everything (draft,
 * approve, pay, cancel, add/remove deductions). DOCTOR + STAFF can
 * only read — doctors see their own clinic's payouts; the service
 * layer further scopes the list to the caller's clinic when not an
 * owner.</p>
 *
 * <p><b>Lifecycle routes</b> are POST-only state transitions (not
 * PATCH) to keep the FE's intent explicit in the URL and make audit
 * grouping trivial.</p>
 */
@Tag(name = "Payouts")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payouts")
public class PayoutController {

    private final PayoutService payoutService;

    @Operation(summary = "Create a DRAFT payout for a doctor over a date window")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @PostMapping("/draft")
    public ResponseEntity<ApiResponse<PayoutResponse>> createDraft(
            @Valid @RequestBody PayoutDraftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(payoutService.createDraft(request)));
    }

    @Operation(summary = "List payouts (tenant-scoped; clinic-scoped for non-owners)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PayoutResponse>>> list(
            @RequestParam(required = false) Long doctorProfileId,
            @RequestParam(required = false) PayoutStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
            @ParameterObject @PageableDefault(size = 20, sort = "periodStart", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                payoutService.list(doctorProfileId, status, periodStart, periodEnd, pageable)));
    }

    @Operation(summary = "Get a single payout with deductions + included treatments")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PayoutResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(payoutService.getById(id)));
    }

    @Operation(summary = "Add a deduction line to a DRAFT payout")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @PostMapping("/{id}/deductions")
    public ResponseEntity<ApiResponse<PayoutResponse>> addDeduction(
            @PathVariable Long id,
            @Valid @RequestBody PayoutDeductionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(payoutService.addDeduction(id, request)));
    }

    @Operation(summary = "Remove a deduction line from a DRAFT payout")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @DeleteMapping("/{id}/deductions/{deductionId}")
    public ResponseEntity<ApiResponse<PayoutResponse>> removeDeduction(
            @PathVariable Long id,
            @PathVariable Long deductionId) {
        return ResponseEntity.ok(ApiResponse.ok(payoutService.removeDeduction(id, deductionId)));
    }

    @Operation(summary = "Recalculate gross/net on a DRAFT payout using live treatment data")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @PostMapping("/{id}/recalculate")
    public ResponseEntity<ApiResponse<PayoutResponse>> recalculate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(payoutService.recalculate(id)));
    }

    @Operation(summary = "Approve DRAFT payout → locks included treatments, freezes snapshots")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<PayoutResponse>> approve(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(payoutService.approve(id)));
    }

    @Operation(summary = "Mark APPROVED payout as PAID")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @PostMapping("/{id}/mark-paid")
    public ResponseEntity<ApiResponse<PayoutResponse>> markPaid(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(payoutService.markPaid(id)));
    }

    @Operation(summary = "Cancel a DRAFT payout (abandoned / wrong)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<PayoutResponse>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(payoutService.cancel(id)));
    }
}
