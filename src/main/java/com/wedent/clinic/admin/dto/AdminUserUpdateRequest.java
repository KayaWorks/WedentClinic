package com.wedent.clinic.admin.dto;

import com.wedent.clinic.employee.entity.EmployeeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update — every field is optional, the service only touches the
 * ones that arrive non-null. Email is not editable here on purpose: it
 * is the login identity and has its own flow (renames are rare and
 * security-sensitive; rolling them through a generic PATCH would invite
 * accidents).
 */
public record AdminUserUpdateRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 30)
        @Pattern(regexp = "^[+0-9 ()\\-]*$",
                message = "phone may only contain digits, spaces, parentheses, dashes and +")
        String phone,
        Long clinicId,
        EmployeeType employeeType,
        @Valid DoctorProfilePayload doctorProfile
) {}
