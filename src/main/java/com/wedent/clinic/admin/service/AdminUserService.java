package com.wedent.clinic.admin.service;

import com.wedent.clinic.admin.dto.AdminResetPasswordResponse;
import com.wedent.clinic.admin.dto.AdminRoleAssignRequest;
import com.wedent.clinic.admin.dto.AdminUserCreateRequest;
import com.wedent.clinic.admin.dto.AdminUserResponse;
import com.wedent.clinic.admin.dto.AdminUserUpdateRequest;
import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.user.entity.RoleType;
import com.wedent.clinic.user.entity.UserStatus;
import org.springframework.data.domain.Pageable;

/**
 * Admin-scoped user management surface. Every method is tenant-scoped
 * through {@link com.wedent.clinic.security.SecurityUtils#currentCompanyId()};
 * the service never accepts a company id from callers and never leaks
 * users across tenant boundaries.
 *
 * <p>Two invariants the implementation guards for all mutating calls:
 * <ul>
 *   <li><b>Last-owner protection</b> — the company must always have at
 *       least one {@code ACTIVE} {@code CLINIC_OWNER}. Disabling the
 *       final one, or stripping their owner role, is rejected with
 *       {@code BUSINESS_RULE_VIOLATION}.</li>
 *   <li><b>Session revocation on deactivate/reset</b> — both flows
 *       revoke every live refresh token for the target user; the admin
 *       flow is implicitly a "log them out everywhere" action.</li>
 * </ul>
 */
public interface AdminUserService {

    PageResponse<AdminUserResponse> search(String search, RoleType role, UserStatus status,
                                           Long clinicId, Pageable pageable);

    AdminUserResponse getById(Long userId);

    AdminUserResponse create(AdminUserCreateRequest request, String ipAddress);

    AdminUserResponse update(Long userId, AdminUserUpdateRequest request, String ipAddress);

    AdminUserResponse assignRoles(Long userId, AdminRoleAssignRequest request, String ipAddress);

    AdminUserResponse removeRole(Long userId, Long roleId, String ipAddress);

    AdminUserResponse activate(Long userId, String ipAddress);

    AdminUserResponse deactivate(Long userId, String ipAddress);

    AdminResetPasswordResponse resetPassword(Long userId, String ipAddress);
}
