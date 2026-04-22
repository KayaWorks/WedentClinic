-- =============================================================
-- V5: Appointments
-- =============================================================

CREATE TABLE appointments (
    id                    BIGSERIAL PRIMARY KEY,
    company_id            BIGINT       NOT NULL REFERENCES companies(id),
    clinic_id             BIGINT       NOT NULL REFERENCES clinics(id),
    patient_id            BIGINT       NOT NULL REFERENCES patients(id),
    doctor_employee_id    BIGINT       NOT NULL REFERENCES employees(id),
    appointment_date      DATE         NOT NULL,
    start_time            TIME         NOT NULL,
    end_time              TIME         NOT NULL,
    status                VARCHAR(30)  NOT NULL DEFAULT 'CREATED',
    note                  VARCHAR(2000),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by            BIGINT,
    updated_by            BIGINT,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_appointment_time CHECK (end_time > start_time)
);

CREATE INDEX idx_appt_clinic_date   ON appointments(clinic_id, appointment_date);
CREATE INDEX idx_appt_doctor_date   ON appointments(doctor_employee_id, appointment_date);
CREATE INDEX idx_appt_patient       ON appointments(patient_id);
CREATE INDEX idx_appt_status        ON appointments(status);
