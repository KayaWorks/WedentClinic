package com.wedent.clinic.patient.mapper;

import com.wedent.clinic.common.mapper.CommonMapperConfig;
import com.wedent.clinic.patient.dto.PatientCreateRequest;
import com.wedent.clinic.patient.dto.PatientResponse;
import com.wedent.clinic.patient.dto.PatientUpdateRequest;
import com.wedent.clinic.patient.entity.Patient;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CommonMapperConfig.class)
public interface PatientMapper {

    Patient toEntity(PatientCreateRequest request);

    void updateEntity(PatientUpdateRequest request, @MappingTarget Patient entity);

    @Mapping(target = "companyId", source = "company.id")
    @Mapping(target = "clinicId",  source = "clinic.id")
    PatientResponse toResponse(Patient entity);
}
