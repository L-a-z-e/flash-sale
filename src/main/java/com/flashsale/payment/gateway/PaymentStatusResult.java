package com.flashsale.payment.gateway;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentStatusResult {

    private final String pgPaymentKey;
    private final String pgStatus;
    private final String orderId;
}
