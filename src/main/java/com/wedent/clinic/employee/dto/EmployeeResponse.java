package com.wedent.clinic.employee.dto;

import com.wedent.clinic.employee.entity.EmployeeStatus;
import com.wedent.clinic.employee.entity.EmployeeType;

public record EmployeeResponse(
        Long id,
        Long companyId,
        Long clinicId,
        Long userId,
        String firstName,
        String lastName,
        String phone,
        String email,
        String identityNumber,
        EmployeeType employeeType,
        EmployeeStatus status
) {}
