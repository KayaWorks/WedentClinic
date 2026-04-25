package com.wedent.clinic.admin.dto;

import com.wedent.clinic.employee.entity.EmployeeType;
import com.wedent.clinic.user.entity.UserStatus;

import java.time.Instant;
import java.util.Set;

/**
 * Admin-side read-model. Flattens the user + employee + doctorProfile
 * trio so the admin UI's list/detail views never have to stitch three
 * calls together. {@code temporaryPassword} is only populated by the
 * create response (rest of the time it is {@code null} and stripped by
 * the {@code @JsonInclude(NON_NULL)} on {@link com.wedent.clinic.common.dto.ApiResponse}).
 */
public record AdminUserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        UserStatus status,
        Long companyId,
        Long clinicId,
        String clinicName,
        Long employeeId,
        EmployeeType employeeType,
        Long doctorProfileId,
        String specialty,
        java.math.BigDecimal commissionRate,
        java.math.BigDecimal fixedSalary,
        Set<AdminRoleSummary> roles,
        Instant createdAt,
        Instant updatedAt,
        String temporaryPassword
) {}
