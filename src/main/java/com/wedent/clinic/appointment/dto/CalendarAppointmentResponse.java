package com.wedent.clinic.appointment.dto;

import com.wedent.clinic.appointment.entity.AppointmentStatus;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Denormalized slim row for the calendar view.
 *
 * <p>Flat on purpose: calendar UIs render hundreds of cells per frame and
 * each deep-linked object costs a JS-side indirection. Names are
 * pre-concatenated, {@code colorKey} is pre-resolved, and the
 * {@code isRecurringInstance} flag is already present so the FE never
 * needs a conditional on a missing field once recurrence ships.</p>
 *
 * <p>{@code colorKey} is a stable UI color hint keyed to status. The FE
 * can either consume it directly (Tailwind token) or remap it.
 * When recurrence ships, instances inherit the parent's color via the
 * same mapping, so no schema churn is expected.</p>
 *
 * <p>{@code isRecurringInstance} is reserved for the upcoming recurrence
 * sprint. It is always {@code false} today — every appointment is a
 * standalone row — but the field lives on the response from day one so
 * client code can branch on it now without a breaking change later.</p>
 */
public record CalendarAppointmentResponse(
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
        String colorKey,
        boolean isRecurringInstance
) {}
