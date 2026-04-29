package com.wedent.clinic.file.entity;

/**
 * Classifies the clinical or administrative purpose of an uploaded file.
 * Values mirror the DB CHECK constraint in V13__patient_files.sql.
 */
public enum PatientFileCategory {
    PANORAMIC_XRAY,
    PERIAPICAL_XRAY,
    CONSENT_FORM,
    TREATMENT_PLAN,
    PAYMENT_RECEIPT,
    IDENTITY_DOCUMENT,
    OTHER
}
