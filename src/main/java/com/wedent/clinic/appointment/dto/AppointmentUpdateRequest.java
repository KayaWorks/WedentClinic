package com.wedent.clinic.appointment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentUpdateRequest(
        @NotNull Long doctorEmployeeId,
        @NotNull LocalDate appointmentDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Size(max = 2000) String note
) {}
