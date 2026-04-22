-- =============================================================
-- V3: Employees + DoctorProfile
-- =============================================================

CREATE TABLE employees (
    id               BIGSERIAL PRIMARY KEY,
    company_id       BIGINT       NOT NULL REFERENCES companies(id),
    clinic_id        BIGINT       NOT NULL REFERENCES clinics(id),
    user_id          BIGINT       REFERENCES users(id),
    first_name       VARCHAR(100) NOT NULL,
    last_name        VARCHAR(100) NOT NULL,
    phone            VARCHAR(30),
    email            VARCHAR(150) NOT NULL,
    identity_number  VARCHAR(20),
    employee_type    VARCHAR(30)  NOT NULL,
    status           VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       BIGINT,
    updated_by       BIGINT,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_employees_clinic_email UNIQUE (clinic_id, email)
);
CREATE INDEX idx_employees_company_id ON employees(company_id);
CREATE INDEX idx_employees_clinic_id  ON employees(clinic_id);
CREATE INDEX idx_employees_type       ON employees(employee_type);

CREATE TABLE doctor_profiles (
    id                BIGSERIAL PRIMARY KEY,
    employee_id       BIGINT        NOT NULL UNIQUE REFERENCES employees(id) ON DELETE CASCADE,
    specialty         VARCHAR(150),
    commission_rate   NUMERIC(5, 2),
    fixed_salary      NUMERIC(15, 2),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by        BIGINT,
    updated_by        BIGINT,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_commission_rate CHECK (commission_rate IS NULL OR (commission_rate >= 0 AND commission_rate <= 100))
);
