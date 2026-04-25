package com.wedent.clinic.admin.controller;

import com.wedent.clinic.admin.dto.AdminResetPasswordResponse;
import com.wedent.clinic.admin.dto.AdminRoleAssignRequest;
import com.wedent.clinic.admin.dto.AdminUserCreateRequest;
import com.wedent.clinic.admin.dto.AdminUserResponse;
import com.wedent.clinic.admin.dto.AdminUserUpdateRequest;
import com.wedent.clinic.admin.service.AdminUserService;
import com.wedent.clinic.common.dto.ApiResponse;
import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.user.entity.RoleType;
import com.wedent.clinic.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-scoped user management surface.
 *
 * <p>Gated at the controller layer to {@code CLINIC_OWNER} and
 * {@code MANAGER}; {@code DOCTOR} / {@code STAFF} are blocked. The
 * RBAC intent is "OWNER full, MANAGER with USER_MANAGE, DOCTOR/STAFF
 * denied" — {@code USER_READ} / {@code USER_MANAGE} are granted to
 * MANAGER at the DB seed level (see {@code V11__user_permissions.sql})
 * rather than enforced per endpoint, because JWTs only encode role
 * authorities (not permission codes) in this codebase.</p>
 *
 * <p>Tenant isolation is enforced at the service layer as
 * defense-in-depth: the service never accepts a company id from
 * callers and filters every read through
 * {@code SecurityUtils.currentCompanyId()}.</p>
 */
@Tag(name = "Admin user management")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "Paginated list with search/role/status/clinic filters")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) RoleType role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) Long clinicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Clamp page size to keep a hostile caller from pulling the whole
        // tenant into one response and blowing the role join out of the
        // first-level cache.
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(
                adminUserService.search(search, role, status, clinicId, pageable)));
    }

    @Operation(summary = "Detail view (includes employee + doctor profile)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> get(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.getById(userId)));
    }

    @Operation(summary = "Provision a new user + employee (+ doctor profile if DOCTOR). " +
            "Response surfaces a one-shot temporary password.")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @PostMapping
    public ResponseEntity<ApiResponse<AdminUserResponse>> create(
            @Valid @RequestBody AdminUserCreateRequest request,
            HttpServletRequest httpRequest) {
        AdminUserResponse response = adminUserService.create(request, clientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @Operation(summary = "Partial update (name/phone/clinic/employeeType/doctorProfile)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @PatchMapping("/{userId}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> update(
            @PathVariable Long userId,
            @Valid @RequestBody AdminUserUpdateRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(
                adminUserService.update(userId, request, clientIp(httpRequest))));
    }

    @Operation(summary = "Append roles (idempotent — roles already held are ignored)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @PostMapping("/{userId}/roles")
    public ResponseEntity<ApiResponse<AdminUserResponse>> assignRoles(
            @PathVariable Long userId,
            @Valid @RequestBody AdminRoleAssignRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(
                adminUserService.assignRoles(userId, request, clientIp(httpRequest))));
    }

    @Operation(summary = "Remove a single role from a user (refuses the last active CLINIC_OWNER)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @DeleteMapping("/{userId}/roles/{roleId}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> removeRole(
            @PathVariable Long userId,
            @PathVariable Long roleId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(
                adminUserService.removeRole(userId, roleId, clientIp(httpRequest))));
    }

    @Operation(summary = "Activate a disabled account")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @PostMapping("/{userId}/activate")
    public ResponseEntity<ApiResponse<AdminUserResponse>> activate(
            @PathVariable Long userId, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(
                adminUserService.activate(userId, clientIp(httpRequest))));
    }

    @Operation(summary = "Deactivate an account and revoke every live session. " +
            "Refuses self-lockout and the last active CLINIC_OWNER.")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @PostMapping("/{userId}/deactivate")
    public ResponseEntity<ApiResponse<AdminUserResponse>> deactivate(
            @PathVariable Long userId, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(
                adminUserService.deactivate(userId, clientIp(httpRequest))));
    }

    @Operation(summary = "Generate a one-shot temporary password and revoke every live session.")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<ApiResponse<AdminResetPasswordResponse>> resetPassword(
            @PathVariable Long userId, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(
                adminUserService.resetPassword(userId, clientIp(httpRequest))));
    }

    /**
     * Same IP-resolution policy as {@code AuthController}: honour the first
     * {@code X-Forwarded-For} hop so audit rows reflect real client IPs and
     * not the load balancer.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
