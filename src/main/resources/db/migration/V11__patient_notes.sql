-- =============================================================
-- V11: Patient Notes module
-- =============================================================
--
-- Stores clinical notes attached to a patient. Notes are scoped
-- to company + clinic (inherited from the patient) and always
-- carry an author (user who created the note).
--
-- note_type values (open set kept as VARCHAR for future growth):
--   GENERAL, ANAMNESIS, ALLERGY, PRESCRIPTION, OTHER

CREATE TABLE patient_notes (
    id             BIGSERIAL       PRIMARY KEY,
    company_id     BIGINT          NOT NULL REFERENCES companies(id),
    clinic_id      BIGINT          NOT NULL REFERENCES clinics(id),
    patient_id     BIGINT          NOT NULL REFERENCES patients(id),
    author_user_id BIGINT          NOT NULL REFERENCES users(id),
    note_type      VARCHAR(30)     NOT NULL DEFAULT 'GENERAL',
    title          VARCHAR(200),
    content        TEXT            NOT NULL,
    pinned         BOOLEAN         NOT NULL DEFAULT FALSE,
    -- BaseEntity columns
    created_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by     BIGINT,
    updated_by     BIGINT,
    active         BOOLEAN         NOT NULL DEFAULT TRUE,
    version        BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT ck_patient_notes_type
        CHECK (note_type IN ('GENERAL','ANAMNESIS','ALLERGY','PRESCRIPTION','OTHER'))
);

-- Hot paths:
--   * List for a patient (newest first)         → (patient_id, active, created_at)
--   * Tenant-scoped query                       → (company_id)
--   * Pinned notes first for a patient          → (patient_id, pinned)
CREATE INDEX idx_patient_notes_patient
    ON patient_notes(patient_id, active, created_at DESC);
CREATE INDEX idx_patient_notes_company
    ON patient_notes(company_id);
CREATE INDEX idx_patient_notes_pinned
    ON patient_notes(patient_id, pinned) WHERE active = TRUE;
