package com.wedent.clinic.dashboard.controller;

import com.wedent.clinic.common.dto.ApiResponse;
import com.wedent.clinic.dashboard.dto.DashboardSummaryResponse;
import com.wedent.clinic.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Landing-page summary with optional clinic + doctor drill-down")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> summary(
            @RequestParam(required = false) Long clinicId,
            @RequestParam(required = false) Long doctorId) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.summary(clinicId, doctorId)));
    }
}
