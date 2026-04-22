package com.wedent.clinic.employee.dto;

import java.math.BigDecimal;

public record DoctorProfileResponse(
        Long id,
        Long employeeId,
        String specialty,
        BigDecimal commissionRate,
        BigDecimal fixedSalary
) {}
