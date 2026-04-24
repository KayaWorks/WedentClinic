-- =============================================================
-- V9: Treatments (per-patient procedure log feeding the payout module)
-- =============================================================
--
-- A treatment record captures one procedure performed on a patient by a
-- specific doctor.  Once status = COMPLETED the {doctor_id, fee} pair feeds
-- the doctor's gross revenue for the period; the payouts module marks each
-- consumed treatment with payout_locked_at to prevent retro-edits that
-- would silently shift an already-issued payout.

CREATE TABLE treatments (
    id                BIGSERIAL    PRIMARY KEY,
    company_id        BIGINT       NOT NULL REFERENCES companies(id),
    clinic_id         BIGINT       NOT NULL REFERENCES clinics(id),
    patient_id        BIGINT       NOT NULL REFERENCES patients(id),
    doctor_id         BIGINT       NOT NULL REFERENCES employees(id),
    name              VARCHAR(200) NOT NULL,
    tooth_number      VARCHAR(8),
    notes             VARCHAR(2000),
    performed_at      TIMESTAMPTZ  NOT NULL,
    fee               NUMERIC(12, 2) NOT NULL,
    currency          VARCHAR(8)   NOT NULL DEFAULT 'TRY',
    status            VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    payout_locked_at  TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        BIGINT,
    updated_by        BIGINT,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_treatments_fee_positive CHECK (fee > 0),
    CONSTRAINT ck_treatments_status CHECK (status IN ('PLANNED', 'COMPLETED', 'CANCELLED'))
);

-- Hot paths:
--   * Patient profile timeline    → (patient_id, performed_at DESC)
--   * Doctor payout aggregation   → (doctor_id, status, performed_at)
--   * Clinic-scope reporting      → (clinic_id, performed_at)
CREATE INDEX idx_treatments_patient_performed
    ON treatments(patient_id, performed_at DESC);
CREATE INDEX idx_treatments_doctor_status_performed
    ON treatments(doctor_id, status, performed_at);
CREATE INDEX idx_treatments_clinic_performed
    ON treatments(clinic_id, performed_at);
CREATE INDEX idx_treatments_company_id
    ON treatments(company_id);
