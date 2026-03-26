package com.flashsale.payment.gateway;

/**
 * PG 추상화 인터페이스.
 * 구현체 교체만으로 PG 전환 가능.
 */
public interface PaymentGateway {

    PaymentApproveResult approve(PaymentApproveRequest request);

    PaymentStatusResult getStatus(String pgPaymentKey);

    PaymentStatusResult getStatusByOrderId(String orderId);

    PaymentCancelResult cancel(String pgPaymentKey, String reason);
}
