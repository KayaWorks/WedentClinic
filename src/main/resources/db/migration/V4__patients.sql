-- =============================================================
-- V4: Patients
-- =============================================================

CREATE TABLE patients (
    id           BIGSERIAL PRIMARY KEY,
    company_id   BIGINT       NOT NULL REFERENCES companies(id),
    clinic_id    BIGINT       NOT NULL REFERENCES clinics(id),
    first_name   VARCHAR(100) NOT NULL,
    last_name    VARCHAR(100) NOT NULL,
    phone        VARCHAR(30)  NOT NULL,
    email        VARCHAR(150),
    birth_date   DATE,
    gender       VARCHAR(20),
    notes        VARCHAR(2000),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by   BIGINT,
    updated_by   BIGINT,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_patients_company_phone UNIQUE (company_id, phone)
);
CREATE INDEX idx_patients_company_id ON patients(company_id);
CREATE INDEX idx_patients_clinic_id  ON patients(clinic_id);
CREATE INDEX idx_patients_last_name  ON patients(last_name);
