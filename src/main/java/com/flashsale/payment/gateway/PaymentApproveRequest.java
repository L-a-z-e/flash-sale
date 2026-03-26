package com.flashsale.payment.gateway;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PaymentApproveRequest {

    private final String orderId;
    private final BigDecimal amount;
    private final String orderName;
    private final String idempotencyKey;
}
