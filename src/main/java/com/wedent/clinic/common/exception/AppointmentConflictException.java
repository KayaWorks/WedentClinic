package com.wedent.clinic.common.exception;

public class AppointmentConflictException extends BusinessException {

    public AppointmentConflictException(String message) {
        super(ErrorCode.APPOINTMENT_CONFLICT, message);
    }
}
