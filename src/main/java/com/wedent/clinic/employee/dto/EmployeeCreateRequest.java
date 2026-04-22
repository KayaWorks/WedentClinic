package com.wedent.clinic.employee.dto;

import com.wedent.clinic.employee.entity.EmployeeType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EmployeeCreateRequest(
        @NotNull Long clinicId,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 30) String phone,
        @NotBlank @Email @Size(max = 150) String email,
        @Size(max = 20) String identityNumber,
        @NotNull EmployeeType employeeType
) {}
