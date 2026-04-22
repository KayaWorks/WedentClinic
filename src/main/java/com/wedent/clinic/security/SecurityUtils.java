package com.wedent.clinic.security;

import com.wedent.clinic.common.exception.ErrorCode;
import com.wedent.clinic.common.exception.BusinessException;
import com.wedent.clinic.common.exception.TenantScopeViolationException;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@UtilityClass
public class SecurityUtils {

    public static final String ROLE_CLINIC_OWNER = "CLINIC_OWNER";
    public static final String ROLE_MANAGER = "MANAGER";
    public static final String ROLE_DOCTOR = "DOCTOR";
    public static final String ROLE_STAFF = "STAFF";

    public static Optional<AuthenticatedUser> currentUserOptional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public static AuthenticatedUser currentUser() {
        return currentUserOptional()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "No authenticated user in context"));
    }

    public static Long currentCompanyId() {
        Long id = currentUser().companyId();
        if (id == null) {
            throw new TenantScopeViolationException("Authenticated user has no company scope");
        }
        return id;
    }

    public static Optional<Long> currentClinicId() {
        return Optional.ofNullable(currentUser().clinicId());
    }

    /**
     * Best-effort user id for non-critical paths like audit logging where the
     * absence of an authenticated user should not throw. For the authoritative
     * throwing variant use {@link #currentUser()}.{@code userId()}.
     */
    public static Long currentUserIdOrNull() {
        return currentUserOptional().map(AuthenticatedUser::userId).orElse(null);
    }

    public static boolean hasRole(String role) {
        return currentUserOptional()
                .map(u -> u.roles().contains(role))
                .orElse(false);
    }

    public static void verifyCompanyScope(Long companyId) {
        if (!currentCompanyId().equals(companyId)) {
            throw new TenantScopeViolationException("Company scope mismatch");
        }
    }

    public static void verifyClinicAccess(Long clinicId) {
        if (hasRole(ROLE_CLINIC_OWNER)) {
            return;
        }
        Long userClinic = currentUser().clinicId();
        if (userClinic == null || !userClinic.equals(clinicId)) {
            throw new TenantScopeViolationException("Clinic scope mismatch");
        }
    }
}
