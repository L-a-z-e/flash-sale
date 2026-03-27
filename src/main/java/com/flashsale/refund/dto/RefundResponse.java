package com.flashsale.refund.dto;

import com.flashsale.refund.domain.Refund;
import com.flashsale.refund.domain.RefundStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RefundResponse(
        Long refundId,
        Long paymentId,
        BigDecimal amount,
        RefundStatus status,
        BigDecimal refundableAmount,
        String reason,
        String message,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
) {
    public static RefundResponse from(Refund refund, BigDecimal refundableAmount, String message) {
        return new RefundResponse(
                refund.getId(),
                refund.getPaymentId(),
                refund.getAmount(),
                refund.getStatus(),
                refundableAmount,
                refund.getReason(),
                message,
                refund.getRequestedAt(),
                refund.getCompletedAt()
        );
    }
}
