-- =============================================================
-- V13: Patient files (blob storage)
-- =============================================================
--
-- Stores binary attachments (X-rays, consent forms, etc.) scoped
-- to a patient.  The actual file bytes live in the `content` column
-- (PostgreSQL bytea) so no external storage volume is required.
--
-- Hot paths:
--   * List metadata for a patient  → (patient_id, active, created_at)
--   * Tenant-scoped access control → (company_id, active)

CREATE TABLE patient_files (
    id              BIGSERIAL       PRIMARY KEY,
    company_id      BIGINT          NOT NULL REFERENCES companies(id),
    clinic_id       BIGINT          NOT NULL REFERENCES clinics(id),
    patient_id      BIGINT          NOT NULL REFERENCES patients(id),
    uploaded_by     BIGINT          REFERENCES users(id),
    category        VARCHAR(30)     NOT NULL DEFAULT 'OTHER',
    file_name       VARCHAR(255)    NOT NULL,
    mime_type       VARCHAR(100)    NOT NULL,
    file_size_bytes BIGINT          NOT NULL,
    description     VARCHAR(500),
    content         BYTEA           NOT NULL,
    -- BaseEntity columns
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by      BIGINT,
    updated_by      BIGINT,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    version         BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT ck_patient_files_category CHECK (
        category IN (
            'PANORAMIC_XRAY', 'PERIAPICAL_XRAY', 'CONSENT_FORM',
            'TREATMENT_PLAN', 'PAYMENT_RECEIPT', 'IDENTITY_DOCUMENT', 'OTHER'
        )
    ),
    CONSTRAINT ck_patient_files_size CHECK (file_size_bytes > 0)
);

CREATE INDEX idx_patient_files_patient
    ON patient_files(patient_id, active, created_at DESC);
CREATE INDEX idx_patient_files_company
    ON patient_files(company_id, active);
