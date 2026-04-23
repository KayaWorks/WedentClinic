package com.wedent.clinic.patient.dto;

/**
 * Tiny payload for {@code GET /api/patients/count}. Wrapping the number in an
 * object (rather than returning a raw long) keeps the response extensible
 * without a breaking change — callers can skim {@code total} today and we can
 * add {@code byClinic} / {@code byGender} later without churning clients.
 */
public record PatientCountResponse(Long clinicId, long total) {}
