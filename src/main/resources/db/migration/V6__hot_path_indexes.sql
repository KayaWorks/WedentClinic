-- =============================================================
-- V6: Hot-path composite indexes
--
-- Adds composite indexes that match the exact predicate shapes of the most
-- frequent queries, letting Postgres use index-only scans instead of the
-- single-column indexes introduced in V1-V5 (which required filter steps
-- at read time). `active = true` is included where soft-delete @SQLRestriction
-- forces every read to carry that predicate — without it the planner can
-- still use the index but needs a heap fetch to discard soft-deleted rows.
-- =============================================================

-- Conflict lookup + search on doctor/day filtered by active + status.
-- Covers AppointmentRepository.findConflictsForUpdate and the doctor-day
-- view that schedulers use most.
CREATE INDEX IF NOT EXISTS idx_appt_doctor_date_status_active
    ON appointments (doctor_employee_id, appointment_date, status)
    WHERE active = true;

-- Company-scoped tenant search (WHERE company_id = ? AND date = ?).
CREATE INDEX IF NOT EXISTS idx_appt_company_date_active
    ON appointments (company_id, appointment_date)
    WHERE active = true;

-- Patient's appointment history, chronological.
CREATE INDEX IF NOT EXISTS idx_appt_patient_date_active
    ON appointments (patient_id, appointment_date DESC)
    WHERE active = true;

-- Patient phone lookup within a company (covers existsByCompanyIdAndPhone).
-- Partial on active patients so the soft-delete filter is free.
CREATE INDEX IF NOT EXISTS idx_patients_company_phone_active
    ON patients (company_id, phone)
    WHERE active = true;

-- Employee email-in-clinic uniqueness check (covers existsByClinicIdAndEmail).
-- Case-insensitive because the repository uses ...IgnoreCase().
CREATE INDEX IF NOT EXISTS idx_employees_clinic_email_lower_active
    ON employees (clinic_id, LOWER(email))
    WHERE active = true;

-- User login lookup (covers findByEmailIgnoreCase). Email is also uniquely
-- constrained in V1 but on raw case; this mirrors the query shape precisely.
CREATE INDEX IF NOT EXISTS idx_users_email_lower_active
    ON users (LOWER(email))
    WHERE active = true;
