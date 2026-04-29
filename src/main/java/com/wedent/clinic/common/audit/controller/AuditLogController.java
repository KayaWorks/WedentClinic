package com.wedent.clinic.common.audit.controller;

import com.wedent.clinic.common.audit.dto.AuditLogDto;
import com.wedent.clinic.common.audit.service.AuditLogService;
import com.wedent.clinic.common.dto.ApiResponse;
import com.wedent.clinic.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Read-only activity feed for patients")
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * Per-patient activity timeline.
     *
     * <p>Requires at least STAFF role so that receptionist-level users can
     * view the activity log but patients/guests cannot.</p>
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @Operation(summary = "List audit log entries for a patient")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogDto>>> listForPatient(
            @RequestParam Long patientId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        // Cap page size to prevent abuse
        int safeSize = Math.min(size, 50);
        PageRequest pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "occurredAt"));

        PageResponse<AuditLogDto> result = auditLogService.listForPatient(patientId, eventType, targetType, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
