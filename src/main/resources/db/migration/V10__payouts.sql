-- =============================================================
-- V10: Payout (Hakediş) module
-- =============================================================
--
-- Hakediş is calculated per-doctor per-period. Pipeline:
--
--   DRAFT    → recalculable, deductions can be added/removed
--   APPROVED → immutable, included treatments are locked
--   PAID     → terminal (paid_at stamped)
--   CANCELLED→ terminal (wrong drafts)
--
-- Approval is the critical transition: it snapshots the commission rate
-- + treatment total onto the period (so later DoctorProfile / Treatment
-- edits cannot retroactively shift an issued payout) and sets
-- treatments.payout_locked_at + treatments.payout_period_id for every
-- included row — both inside a single DB transaction.

-- -------------------------------------------------------------
-- Extend treatments: completedAt (when status→COMPLETED flipped)
-- and payout_period_id (FK to the period that consumed this treatment).
-- -------------------------------------------------------------
ALTER TABLE treatments
    ADD COLUMN completed_at     TIMESTAMPTZ,
    ADD COLUMN payout_period_id BIGINT;

-- Backfill: treatments already stored as COMPLETED get completed_at
-- seeded from their performed_at so aggregation still works retroactively.
UPDATE treatments
SET completed_at = performed_at
WHERE status = 'COMPLETED' AND completed_at IS NULL;

-- Doctor payout aggregation now keys off completed_at (not performed_at)
-- because a procedure can be performed on day X but billed/completed on Y.
CREATE INDEX idx_treatments_doctor_status_completed
    ON treatments(doctor_id, status, completed_at);

CREATE INDEX idx_treatments_payout_period
    ON treatments(payout_period_id);

-- -------------------------------------------------------------
-- payout_periods — one row per (doctor, period) hakediş run.
-- -------------------------------------------------------------
CREATE TABLE payout_periods (
    id                          BIGSERIAL      PRIMARY KEY,
    company_id                  BIGINT         NOT NULL REFERENCES companies(id),
    clinic_id                   BIGINT         NOT NULL REFERENCES clinics(id),
    doctor_profile_id           BIGINT         NOT NULL REFERENCES doctor_profiles(id),
    period_start                DATE           NOT NULL,
    period_end                  DATE           NOT NULL,
    status                      VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    -- Snapshots captured at APPROVE time. NULL while in DRAFT.
    treatment_total_snapshot    NUMERIC(14, 2),
    commission_rate_snapshot    NUMERIC(5, 2),
    -- Computed figures (kept live while DRAFT, frozen at APPROVE).
    gross_amount                NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_deduction             NUMERIC(14, 2) NOT NULL DEFAULT 0,
    net_amount                  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    approved_at                 TIMESTAMPTZ,
    paid_at                     TIMESTAMPTZ,
    -- BaseEntity columns
    created_at                  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by                  BIGINT,
    updated_by                  BIGINT,
    active                      BOOLEAN        NOT NULL DEFAULT TRUE,
    version                     BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_payout_periods_status CHECK (status IN ('DRAFT','APPROVED','PAID','CANCELLED')),
    CONSTRAINT ck_payout_periods_window CHECK (period_start < period_end)
);

-- Late-bound FK from treatments back to payout_periods (avoids forward-ref
-- on CREATE ordering). Deliberately no cascade — a payout row should
-- never disappear while it still holds locked treatments.
ALTER TABLE treatments
    ADD CONSTRAINT fk_treatments_payout_period
        FOREIGN KEY (payout_period_id) REFERENCES payout_periods(id);

-- Hot paths:
--   * List filter by doctor + status             → (doctor_profile_id, status)
--   * Tenant-scoped list + date filter           → (company_id, period_start)
--   * Clinic-scope dashboards                    → (clinic_id, status, period_start)
CREATE INDEX idx_payout_periods_doctor_status
    ON payout_periods(doctor_profile_id, status);
CREATE INDEX idx_payout_periods_company_period
    ON payout_periods(company_id, period_start DESC);
CREATE INDEX idx_payout_periods_clinic_status_period
    ON payout_periods(clinic_id, status, period_start);

-- -------------------------------------------------------------
-- payout_deductions — free-form expense lines subtracted from gross.
-- -------------------------------------------------------------
CREATE TABLE payout_deductions (
    id                 BIGSERIAL      PRIMARY KEY,
    payout_period_id   BIGINT         NOT NULL REFERENCES payout_periods(id) ON DELETE CASCADE,
    type               VARCHAR(30)    NOT NULL,
    description        VARCHAR(500),
    amount             NUMERIC(12, 2) NOT NULL,
    -- BaseEntity columns
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by         BIGINT,
    updated_by         BIGINT,
    active             BOOLEAN        NOT NULL DEFAULT TRUE,
    version            BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_payout_deductions_amount CHECK (amount > 0),
    CONSTRAINT ck_payout_deductions_type
        CHECK (type IN ('LAB','MATERIAL','ADVANCE_PAYMENT','PENALTY','OTHER'))
);

CREATE INDEX idx_payout_deductions_period
    ON payout_deductions(payout_period_id);
