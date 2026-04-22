package com.wedent.clinic.employee.mapper;

import com.wedent.clinic.common.mapper.CommonMapperConfig;
import com.wedent.clinic.employee.dto.DoctorProfileRequest;
import com.wedent.clinic.employee.dto.DoctorProfileResponse;
import com.wedent.clinic.employee.dto.EmployeeCreateRequest;
import com.wedent.clinic.employee.dto.EmployeeResponse;
import com.wedent.clinic.employee.dto.EmployeeUpdateRequest;
import com.wedent.clinic.employee.entity.DoctorProfile;
import com.wedent.clinic.employee.entity.Employee;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CommonMapperConfig.class)
public interface EmployeeMapper {

    Employee toEntity(EmployeeCreateRequest request);

    void updateEntity(EmployeeUpdateRequest request, @MappingTarget Employee entity);

    @Mapping(target = "companyId", source = "company.id")
    @Mapping(target = "clinicId",  source = "clinic.id")
    @Mapping(target = "userId",    source = "user.id")
    EmployeeResponse toResponse(Employee entity);

    DoctorProfile toDoctorProfile(DoctorProfileRequest request);

    void updateDoctorProfile(DoctorProfileRequest request, @MappingTarget DoctorProfile profile);

    @Mapping(target = "employeeId", source = "employee.id")
    DoctorProfileResponse toDoctorProfileResponse(DoctorProfile profile);
}
