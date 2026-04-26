package com.wedent.clinic.note.dto;

import com.wedent.clinic.note.entity.NoteType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PatientNoteCreateRequest(

        NoteType noteType,

        @Size(max = 200)
        String title,

        @NotBlank
        @Size(max = 10_000)
        String content,

        Boolean pinned
) {}
