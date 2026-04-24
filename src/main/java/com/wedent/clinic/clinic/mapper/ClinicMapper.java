package com.wedent.clinic.clinic.mapper;

import com.wedent.clinic.clinic.dto.ClinicCreateRequest;
import com.wedent.clinic.clinic.dto.ClinicResponse;
import com.wedent.clinic.clinic.dto.ClinicUpdateRequest;
import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.common.mapper.CommonMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CommonMapperConfig.class)
public interface ClinicMapper {

    /** Company is attached by the service from the caller's tenant root. */
    Clinic toEntity(ClinicCreateRequest request);

    /**
     * Nulls skipped (see {@link CommonMapperConfig}), so PATCH semantics fall
     * out naturally — only fields present in the request overwrite.
     */
    void updateEntity(ClinicUpdateRequest request, @MappingTarget Clinic entity);

    @Mapping(target = "companyId", source = "company.id")
    ClinicResponse toResponse(Clinic entity);
}
