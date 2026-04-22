package com.wedent.clinic.appointment.mapper;

import com.wedent.clinic.appointment.dto.AppointmentResponse;
import com.wedent.clinic.appointment.entity.Appointment;
import com.wedent.clinic.common.mapper.CommonMapperConfig;
import com.wedent.clinic.employee.entity.Employee;
import com.wedent.clinic.patient.entity.Patient;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(config = CommonMapperConfig.class)
public interface AppointmentMapper {

    @Mapping(target = "clinicId",         source = "clinic.id")
    @Mapping(target = "patientId",        source = "patient.id")
    @Mapping(target = "patientFullName",  source = "patient", qualifiedByName = "patientFullName")
    @Mapping(target = "doctorEmployeeId", source = "doctor.id")
    @Mapping(target = "doctorFullName",   source = "doctor",  qualifiedByName = "doctorFullName")
    AppointmentResponse toResponse(Appointment entity);

    @Named("patientFullName")
    default String patientFullName(Patient p) {
        return p == null ? null : "%s %s".formatted(p.getFirstName(), p.getLastName());
    }

    @Named("doctorFullName")
    default String doctorFullName(Employee e) {
        return e == null ? null : "%s %s".formatted(e.getFirstName(), e.getLastName());
    }
}
