package com.wedent.clinic.company.controller;

import com.wedent.clinic.common.dto.ApiResponse;
import com.wedent.clinic.company.dto.CompanyResponse;
import com.wedent.clinic.company.dto.CompanyUpdateRequest;
import com.wedent.clinic.company.service.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Everything is pinned to {@code /current} — cross-tenant company lookup is
 * deliberately absent. Reads are open to every authenticated role; writes
 * are owner-only.
 */
@Tag(name = "Companies")
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @Operation(summary = "Fetch the caller's own company (tenant root)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCurrent() {
        return ResponseEntity.ok(ApiResponse.ok(companyService.getCurrent()));
    }

    @Operation(summary = "Partially update the caller's own company (owner-only)")
    @PreAuthorize("hasRole('CLINIC_OWNER')")
    @PatchMapping("/current")
    public ResponseEntity<ApiResponse<CompanyResponse>> patchCurrent(
            @Valid @RequestBody CompanyUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(companyService.updateCurrent(request)));
    }

    @Operation(summary = "Replace editable fields on the caller's own company (owner-only)")
    @PreAuthorize("hasRole('CLINIC_OWNER')")
    @PutMapping("/current")
    public ResponseEntity<ApiResponse<CompanyResponse>> putCurrent(
            @Valid @RequestBody CompanyUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(companyService.updateCurrent(request)));
    }

    // ───────────────────────── /me aliases ─────────────────────────
    // Thin aliases delegating to the same service logic so the public
    // API speaks a consistent "me" verb without duplicating business
    // rules, validation, or audit plumbing.

    @Operation(summary = "Alias of GET /current — fetches the caller's own company")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CompanyResponse>> getMe() {
        return ResponseEntity.ok(ApiResponse.ok(companyService.getCurrent()));
    }

    @Operation(summary = "Alias of PATCH /current — partial update (owner-only)")
    @PreAuthorize("hasRole('CLINIC_OWNER')")
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<CompanyResponse>> patchMe(
            @Valid @RequestBody CompanyUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(companyService.updateCurrent(request)));
    }
}
