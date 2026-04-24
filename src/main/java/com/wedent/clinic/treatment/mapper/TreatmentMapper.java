package com.wedent.clinic.treatment.mapper;

import com.wedent.clinic.common.mapper.CommonMapperConfig;
import com.wedent.clinic.treatment.dto.TreatmentCreateRequest;
import com.wedent.clinic.treatment.dto.TreatmentResponse;
import com.wedent.clinic.treatment.dto.TreatmentUpdateRequest;
import com.wedent.clinic.treatment.entity.Treatment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CommonMapperConfig.class)
public interface TreatmentMapper {

    /**
     * Builds the entity skeleton from the create request. Relations
     * (patient, doctor, clinic, company) are wired by the service after
     * tenant + role checks succeed, hence the deliberately empty mappings
     * here so MapStruct doesn't try to read them off the request.
     */
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "clinic", ignore = true)
    @Mapping(target = "patient", ignore = true)
    @Mapping(target = "doctor", ignore = true)
    @Mapping(target = "payoutLockedAt", ignore = true)
    Treatment toEntity(TreatmentCreateRequest request);

    /**
     * Patch-style copy over the existing entity. {@code doctorId} is
     * intentionally absent — re-assignment requires loading the new
     * Employee + tenant verification, which is service-layer work.
     */
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "clinic", ignore = true)
    @Mapping(target = "patient", ignore = true)
    @Mapping(target = "doctor", ignore = true)
    @Mapping(target = "payoutLockedAt", ignore = true)
    void updateEntity(TreatmentUpdateRequest request, @MappingTarget Treatment entity);

    @Mapping(target = "patientId", source = "patient.id")
    @Mapping(target = "doctorId", source = "doctor.id")
    @Mapping(target = "doctorName", expression =
            "java(entity.getDoctor() == null ? null "
            + ": (entity.getDoctor().getFirstName() + \" \" + entity.getDoctor().getLastName()))")
    @Mapping(target = "clinicId", source = "clinic.id")
    @Mapping(target = "companyId", source = "company.id")
    TreatmentResponse toResponse(Treatment entity);
}
