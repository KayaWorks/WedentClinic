package com.wedent.clinic.appointment.dto;

import com.wedent.clinic.appointment.entity.AppointmentStatus;
import jakarta.validation.constraints.NotNull;

public record AppointmentStatusUpdateRequest(
        @NotNull AppointmentStatus status
) {}
