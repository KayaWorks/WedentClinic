package com.wedent.clinic.admin.dto;

import com.wedent.clinic.employee.entity.EmployeeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Admin-side create payload — one atomic call that provisions (a) a login
 * User, (b) an Employee record bound to the selected clinic, and, when the
 * role is DOCTOR, (c) a DoctorProfile row for payout aggregation.
 *
 * <p>Password is intentionally absent: the admin flow generates a
 * single-use temporary password which the response surfaces exactly
 * once. This keeps plaintext passwords out of request bodies (and out
 * of access logs) while still giving the admin something to hand to the
 * new hire out-of-band.
 */
public record AdminUserCreateRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 150) String email,
        @Size(max = 30)
        @Pattern(regexp = "^[+0-9 ()\\-]*$",
                message = "phone may only contain digits, spaces, parentheses, dashes and +")
        String phone,
        @NotNull Long clinicId,
        @NotEmpty Set<@NotNull Long> roleIds,
        @NotNull EmployeeType employeeType,
        @Valid DoctorProfilePayload doctorProfile
) {}
