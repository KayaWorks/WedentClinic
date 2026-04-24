package com.wedent.clinic.user.controller;

import com.wedent.clinic.common.dto.ApiResponse;
import com.wedent.clinic.user.dto.PermissionResponse;
import com.wedent.clinic.user.dto.RoleResponse;
import com.wedent.clinic.user.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * RBAC lookup endpoints. Single controller, two top-level paths — keeping
 * them together makes the cache-invalidation story (one service, two cache
 * names) trivial to reason about.
 *
 * <p>Access is restricted to roles that actually drive user-role assignment
 * (owner + manager). Plain doctors/staff don't need to see the full role
 * graph — their own effective permissions already come back from
 * {@code /api/users/me}.</p>
 */
@Tag(name = "RBAC catalog")
@RestController
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @Operation(summary = "All roles with their permissions (cached)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @GetMapping("/api/roles")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> listRoles() {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.listRoles()));
    }

    @Operation(summary = "All permissions (cached)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @GetMapping("/api/permissions")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> listPermissions() {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.listPermissions()));
    }
}
