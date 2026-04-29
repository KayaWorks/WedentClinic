-- V14: add patient_id column to audit_log for per-patient activity feed queries.
-- Nullable so existing rows remain valid; backfill not needed (historical rows
-- still filterable via the detail JSONB).

ALTER TABLE audit_log
    ADD COLUMN IF NOT EXISTS patient_id BIGINT;

-- Composite index to serve: WHERE company_id = ? AND patient_id = ? ORDER BY occurred_at DESC
CREATE INDEX IF NOT EXISTS idx_audit_log_patient
    ON audit_log (company_id, patient_id, occurred_at DESC)
    WHERE patient_id IS NOT NULL;
