package com.wedent.clinic.session.controller;

import com.wedent.clinic.common.dto.ApiResponse;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
import com.wedent.clinic.session.dto.SessionResponse;
import com.wedent.clinic.session.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Self-service device management. Mounted under {@code /api/users/me} along
 * with the profile + preferences endpoints.
 *
 * <p>Single revoke deliberately returns 404 on unknown ids so the FE can
 * differentiate "you tried to kill a session that no longer exists" from
 * "we killed it for you" — the underlying service is idempotent, but the
 * HTTP shape is sharper.</p>
 */
@Tag(name = "User sessions")
@RestController
@RequestMapping("/api/users/me/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @Operation(summary = "Live sessions for the caller, newest first")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SessionResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(sessionService.listMine()));
    }

    @Operation(summary = "Revoke a single session of the caller (logout one device)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> revoke(@PathVariable String sessionId) {
        boolean revoked = sessionService.revokeMine(sessionId);
        if (!revoked) {
            throw new ResourceNotFoundException("Session", sessionId);
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @Operation(summary = "Revoke every live session of the caller (logout everywhere)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Map<String, Integer>>> revokeAll() {
        int count = sessionService.revokeAllMine();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("revokedCount", count)));
    }
}
