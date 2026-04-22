package com.wedent.clinic.patient.dto;

import com.wedent.clinic.patient.entity.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PatientCreateRequest(
        @NotNull Long clinicId,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Size(max = 30) String phone,
        @Email @Size(max = 150) String email,
        @Past LocalDate birthDate,
        Gender gender,
        @Size(max = 2000) String notes
) {}
