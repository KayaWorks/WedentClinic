package com.wedent.clinic.employee.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DoctorProfileRequest(
        @Size(max = 150) String specialty,

        @DecimalMin(value = "0.00", inclusive = true)
        @DecimalMax(value = "100.00", inclusive = true)
        BigDecimal commissionRate,

        @DecimalMin(value = "0.00", inclusive = true)
        BigDecimal fixedSalary
) {}
