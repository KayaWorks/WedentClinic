package com.wedent.clinic.note.mapper;

import com.wedent.clinic.common.mapper.CommonMapperConfig;
import com.wedent.clinic.note.dto.PatientNoteCreateRequest;
import com.wedent.clinic.note.dto.PatientNoteResponse;
import com.wedent.clinic.note.dto.PatientNoteUpdateRequest;
import com.wedent.clinic.note.entity.PatientNote;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CommonMapperConfig.class)
public interface PatientNoteMapper {

    /**
     * Creates entity skeleton from the request. Relational fields
     * (company, clinic, patient, author) are wired by the service.
     */
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "clinic", ignore = true)
    @Mapping(target = "patient", ignore = true)
    @Mapping(target = "author", ignore = true)
    PatientNote toEntity(PatientNoteCreateRequest request);

    /**
     * PATCH-style update. NullValuePropertyMappingStrategy.IGNORE means
     * null fields in the request leave the entity field unchanged.
     */
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "clinic", ignore = true)
    @Mapping(target = "patient", ignore = true)
    @Mapping(target = "author", ignore = true)
    void updateEntity(PatientNoteUpdateRequest request, @MappingTarget PatientNote entity);

    @Mapping(target = "patientId", source = "patient.id")
    @Mapping(target = "clinicId", source = "clinic.id")
    @Mapping(target = "companyId", source = "company.id")
    @Mapping(target = "authorUserId", source = "author.id")
    @Mapping(target = "authorName", expression =
            "java(entity.getAuthor() == null ? null "
            + ": (entity.getAuthor().getFirstName() + \" \" + entity.getAuthor().getLastName()))")
    PatientNoteResponse toResponse(PatientNote entity);
}
