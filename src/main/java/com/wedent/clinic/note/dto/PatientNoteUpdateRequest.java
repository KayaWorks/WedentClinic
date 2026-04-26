package com.wedent.clinic.note.dto;

import com.wedent.clinic.note.entity.NoteType;
import jakarta.validation.constraints.Size;

public record PatientNoteUpdateRequest(

        NoteType noteType,

        @Size(max = 200)
        String title,

        @Size(max = 10_000)
        String content,

        Boolean pinned
) {}
