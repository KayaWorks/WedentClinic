package com.wedent.clinic.appointment.dto;

import com.wedent.clinic.appointment.entity.AppointmentStatus;

import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentResponse(
        Long id,
        Long clinicId,
        Long patientId,
        String patientFullName,
        Long doctorEmployeeId,
        String doctorFullName,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        AppointmentStatus status,
        String note
) {}
