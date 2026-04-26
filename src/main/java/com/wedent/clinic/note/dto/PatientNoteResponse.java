package com.wedent.clinic.note.dto;

import com.wedent.clinic.note.entity.NoteType;

import java.time.Instant;

public record PatientNoteResponse(
        Long id,
        Long patientId,
        Long clinicId,
        Long companyId,
        Long authorUserId,
        String authorName,
        NoteType noteType,
        String title,
        String content,
        boolean pinned,
        Instant createdAt,
        Instant updatedAt
) {}
