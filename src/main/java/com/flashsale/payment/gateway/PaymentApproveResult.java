package com.flashsale.payment.gateway;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentApproveResult {

    private final boolean success;
    private final String pgPaymentKey;
    private final String pgStatus;
    private final String method;
    private final LocalDateTime approvedAt;

    // 실패 시
    private final String errorCode;
    private final String errorMessage;
}
