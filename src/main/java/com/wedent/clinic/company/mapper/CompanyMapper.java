package com.wedent.clinic.company.mapper;

import com.wedent.clinic.common.mapper.CommonMapperConfig;
import com.wedent.clinic.company.dto.CompanyResponse;
import com.wedent.clinic.company.dto.CompanyUpdateRequest;
import com.wedent.clinic.company.entity.Company;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(config = CommonMapperConfig.class)
public interface CompanyMapper {

    CompanyResponse toResponse(Company entity);

    /** Nulls skipped → PATCH semantics. See {@link CommonMapperConfig}. */
    void updateEntity(CompanyUpdateRequest request, @MappingTarget Company entity);
}
