package com.wedent.clinic.payment.mapper;

import com.wedent.clinic.common.mapper.CommonMapperConfig;
import com.wedent.clinic.payment.dto.PaymentCreateRequest;
import com.wedent.clinic.payment.dto.PaymentResponse;
import com.wedent.clinic.payment.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CommonMapperConfig.class)
public interface PaymentMapper {

    /**
     * Creates entity skeleton from the create request. Relations
     * (company, clinic, patient) are wired by the service.
     */
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "clinic", ignore = true)
    @Mapping(target = "patient", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "cancelledAt", ignore = true)
    @Mapping(target = "cancelReason", ignore = true)
    Payment toEntity(PaymentCreateRequest request);

    @Mapping(target = "patientId", source = "patient.id")
    @Mapping(target = "clinicId", source = "clinic.id")
    @Mapping(target = "companyId", source = "company.id")
    PaymentResponse toResponse(Payment entity);
}
