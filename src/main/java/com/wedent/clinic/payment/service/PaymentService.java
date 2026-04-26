package com.wedent.clinic.payment.service;

import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.payment.dto.PatientBalanceResponse;
import com.wedent.clinic.payment.dto.PaymentCancelRequest;
import com.wedent.clinic.payment.dto.PaymentCreateRequest;
import com.wedent.clinic.payment.dto.PaymentResponse;
import org.springframework.data.domain.Pageable;

public interface PaymentService {

    PaymentResponse create(Long patientId, PaymentCreateRequest request);

    PageResponse<PaymentResponse> listForPatient(Long patientId, Pageable pageable);

    PaymentResponse getById(Long paymentId);

    PaymentResponse cancel(Long paymentId, PaymentCancelRequest request);

    PatientBalanceResponse getBalance(Long patientId);
}
