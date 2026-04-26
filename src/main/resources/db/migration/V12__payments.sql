-- =============================================================
-- V12: Payments module
-- =============================================================
--
-- Records monetary payments made by/for a patient.
-- A patient's balance = SUM(amount) for COMPLETED payments
--                     - SUM(fee)    for all their treatments.
--
-- status lifecycle:
--   COMPLETED → the normal terminal state (money received)
--   CANCELLED → refund / data-entry correction
--
-- method values: CASH, CARD, BANK_TRANSFER, INSURANCE, OTHER

CREATE TABLE payments (
    id             BIGSERIAL       PRIMARY KEY,
    company_id     BIGINT          NOT NULL REFERENCES companies(id),
    clinic_id      BIGINT          NOT NULL REFERENCES clinics(id),
    patient_id     BIGINT          NOT NULL REFERENCES patients(id),
    amount         NUMERIC(14, 2)  NOT NULL,
    currency       VARCHAR(8)      NOT NULL DEFAULT 'TRY',
    method         VARCHAR(30)     NOT NULL DEFAULT 'CASH',
    status         VARCHAR(20)     NOT NULL DEFAULT 'COMPLETED',
    paid_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    description    VARCHAR(500),
    cancelled_at   TIMESTAMPTZ,
    cancel_reason  VARCHAR(500),
    -- BaseEntity columns
    created_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by     BIGINT,
    updated_by     BIGINT,
    active         BOOLEAN         NOT NULL DEFAULT TRUE,
    version        BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT ck_payments_amount  CHECK (amount > 0),
    CONSTRAINT ck_payments_status  CHECK (status IN ('COMPLETED','CANCELLED')),
    CONSTRAINT ck_payments_method  CHECK (method IN ('CASH','CARD','BANK_TRANSFER','INSURANCE','OTHER'))
);

-- Hot paths:
--   * List for a patient                        → (patient_id, active, paid_at)
--   * Balance aggregation per patient           → (patient_id, status)
--   * Tenant-scoped reporting                   → (company_id, paid_at)
--   * Clinic-scoped reporting                   → (clinic_id, paid_at)
CREATE INDEX idx_payments_patient
    ON payments(patient_id, active, paid_at DESC);
CREATE INDEX idx_payments_patient_status
    ON payments(patient_id, status) WHERE active = TRUE;
CREATE INDEX idx_payments_company_paid_at
    ON payments(company_id, paid_at DESC);
CREATE INDEX idx_payments_clinic_paid_at
    ON payments(clinic_id, paid_at DESC);
