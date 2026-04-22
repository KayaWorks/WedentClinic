package com.wedent.clinic.common.tenant;

import com.wedent.clinic.security.SecurityUtils;
import org.springframework.stereotype.Component;

/**
 * Resolves the effective clinic scope for list/search operations.
 *
 * <p>Rules:
 * <ul>
 *   <li>{@code CLINIC_OWNER} can query any clinic under the company, or all clinics ({@code null}).</li>
 *   <li>Non-owner roles are always clamped to their own {@code clinicId} regardless of what was requested.</li>
 *   <li>If a non-owner omits the filter, their own clinic is injected.</li>
 * </ul>
 * This centralizes an access-control decision that would otherwise be duplicated
 * across every scoped service (employee, patient, appointment).
 */
@Component
public class TenantScopeResolver {

    public Long resolveClinicScope(Long requestedClinicId) {
        if (SecurityUtils.hasRole(SecurityUtils.ROLE_CLINIC_OWNER)) {
            return requestedClinicId;
        }
        Long userClinic = SecurityUtils.currentClinicId().orElse(null);
        if (requestedClinicId != null && userClinic != null && !requestedClinicId.equals(userClinic)) {
            return userClinic;
        }
        return requestedClinicId != null ? requestedClinicId : userClinic;
    }
}
