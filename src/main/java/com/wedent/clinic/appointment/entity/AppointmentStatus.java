package com.wedent.clinic.appointment.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum AppointmentStatus {
    CREATED,
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    NO_SHOW;

    /**
     * Allowed status transitions. Terminal states (CANCELLED, COMPLETED, NO_SHOW) are final.
     */
    private static final Map<AppointmentStatus, Set<AppointmentStatus>> TRANSITIONS = Map.of(
            CREATED,   EnumSet.of(CONFIRMED, CANCELLED, COMPLETED, NO_SHOW),
            CONFIRMED, EnumSet.of(CANCELLED, COMPLETED, NO_SHOW),
            CANCELLED, EnumSet.noneOf(AppointmentStatus.class),
            COMPLETED, EnumSet.noneOf(AppointmentStatus.class),
            NO_SHOW,   EnumSet.noneOf(AppointmentStatus.class)
    );

    public boolean canTransitionTo(AppointmentStatus next) {
        return TRANSITIONS.getOrDefault(this, EnumSet.noneOf(AppointmentStatus.class)).contains(next);
    }

    public boolean isActiveSlot() {
        return this == CREATED || this == CONFIRMED;
    }
}
