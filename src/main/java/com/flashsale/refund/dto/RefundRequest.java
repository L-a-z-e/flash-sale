package com.flashsale.refund.dto;

import java.math.BigDecimal;

public record RefundRequest(
        Long paymentId,
        BigDecimal amount,
        String reason
) {
}
