package com.wedent.clinic.patient.dto;

import com.wedent.clinic.patient.entity.Gender;

import java.time.LocalDate;

public record PatientResponse(
        Long id,
        Long companyId,
        Long clinicId,
        String firstName,
        String lastName,
        String phone,
        String email,
        LocalDate birthDate,
        Gender gender,
        String notes
) {}
