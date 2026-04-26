package com.wedent.clinic.payment.dto;

import jakarta.validation.constraints.Size;

public record PaymentCancelRequest(

        @Size(max = 500)
        String cancelReason
) {}
