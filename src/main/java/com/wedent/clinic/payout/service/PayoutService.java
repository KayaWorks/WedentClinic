package com.wedent.clinic.payout.service;

import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.payout.dto.PayoutDeductionRequest;
import com.wedent.clinic.payout.dto.PayoutDraftRequest;
import com.wedent.clinic.payout.dto.PayoutResponse;
import com.wedent.clinic.payout.entity.PayoutStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface PayoutService {

    PayoutResponse createDraft(PayoutDraftRequest request);

    PageResponse<PayoutResponse> list(Long doctorProfileId,
                                      PayoutStatus status,
                                      LocalDate from,
                                      LocalDate to,
                                      Pageable pageable);

    PayoutResponse getById(Long id);

    PayoutResponse addDeduction(Long id, PayoutDeductionRequest request);

    PayoutResponse removeDeduction(Long id, Long deductionId);

    PayoutResponse recalculate(Long id);

    PayoutResponse approve(Long id);

    PayoutResponse markPaid(Long id);

    PayoutResponse cancel(Long id);
}
