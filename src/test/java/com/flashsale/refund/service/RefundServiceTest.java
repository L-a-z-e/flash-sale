package com.flashsale.refund.service;

import com.flashsale.common.exception.BusinessException;
import com.flashsale.common.exception.ErrorCode;
import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderStatus;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.payment.domain.Payment;
import com.flashsale.payment.domain.PaymentStatus;
import com.flashsale.payment.gateway.PaymentCancelResult;
import com.flashsale.payment.gateway.PaymentGateway;
import com.flashsale.payment.repository.PaymentRepository;
import com.flashsale.refund.domain.Refund;
import com.flashsale.refund.domain.RefundStatus;
import com.flashsale.refund.dto.RefundRequest;
import com.flashsale.refund.dto.RefundResponse;
import com.flashsale.refund.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @InjectMocks
    private RefundService refundService;

    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentGateway paymentGateway;

    private Payment approvedPayment;
    private Order confirmedOrder;

    @BeforeEach
    void setUp() {
        approvedPayment = Payment.builder()
                .orderId(1L)
                .totalAmount(new BigDecimal("50000"))
                .pgProvider("MOCK_PG")
                .build();
        approvedPayment.approve("pg-key-001", "DONE", "CARD", LocalDateTime.now());

        confirmedOrder = Order.builder()
                .userId(1L)
                .timeDealId(1L)
                .quantity(1)
                .totalPrice(new BigDecimal("50000"))
                .idempotencyKey("test-order-1")
                .build();
        confirmedOrder.confirm();
    }

    @Nested
    @DisplayName("Pre-RPC 검증")
    class PreRpcTests {

        @Test
        @DisplayName("결제 없으면 PAYMENT_NOT_FOUND")
        void paymentNotFound() {
            given(paymentRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());

            RefundRequest request = new RefundRequest(99L, new BigDecimal("10000"), "테스트");

            assertThatThrownBy(() -> refundService.preRpc(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("PENDING 상태에서 환불 불가")
        void refundNotAllowedOnPending() {
            Payment pendingPayment = Payment.builder()
                    .orderId(1L)
                    .totalAmount(new BigDecimal("50000"))
                    .pgProvider("MOCK_PG")
                    .build();
            // PENDING 상태 유지

            given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(pendingPayment));

            RefundRequest request = new RefundRequest(1L, new BigDecimal("10000"), "테스트");

            assertThatThrownBy(() -> refundService.preRpc(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.REFUND_NOT_ALLOWED);
        }

        @Test
        @DisplayName("환불 가능 금액 초과 시 REFUND_AMOUNT_EXCEEDED")
        void refundAmountExceeded() {
            given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(approvedPayment));

            RefundRequest request = new RefundRequest(1L, new BigDecimal("99999"), "테스트");

            assertThatThrownBy(() -> refundService.preRpc(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.REFUND_AMOUNT_EXCEEDED);
        }

        @Test
        @DisplayName("Pre-RPC 성공 시 Refund PENDING + refundableAmount 선차감")
        void preRpcSuccess() {
            given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(approvedPayment));
            given(refundRepository.findByPaymentId(any())).willReturn(Collections.emptyList());
            given(refundRepository.findByIdempotencyKey(any())).willReturn(Optional.empty());
            given(refundRepository.save(any(Refund.class))).willAnswer(inv -> inv.getArgument(0));

            RefundRequest request = new RefundRequest(1L, new BigDecimal("30000"), "부분 환불");

            Refund refund = refundService.preRpc(request);

            assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
            assertThat(refund.getAmount()).isEqualByComparingTo(new BigDecimal("30000"));
            assertThat(approvedPayment.getRefundableAmount()).isEqualByComparingTo(new BigDecimal("20000"));
        }
    }

    @Nested
    @DisplayName("Post-RPC 결과 처리")
    class PostRpcTests {

        @Test
        @DisplayName("PG 성공 → SUCCEEDED + 부분 환불 상태")
        void pgSuccessPartialRefund() {
            Refund refund = Refund.builder()
                    .paymentId(1L)
                    .orderId(1L)
                    .idempotencyKey("payment-1-refund-1")
                    .amount(new BigDecimal("30000"))
                    .reason("부분 환불")
                    .build();
            // Pre-RPC에서 선차감된 상태
            approvedPayment.deductRefundable(new BigDecimal("30000"));

            given(refundRepository.findById(any())).willReturn(Optional.of(refund));
            given(paymentRepository.findById(1L)).willReturn(Optional.of(approvedPayment));

            PaymentCancelResult pgResult = PaymentCancelResult.builder()
                    .success(true)
                    .pgPaymentKey("pg-key-001")
                    .pgStatus("PARTIAL_CANCELED")
                    .canceledAt(LocalDateTime.now())
                    .build();

            RefundResponse response = refundService.postRpc(refund.getId(), pgResult);

            assertThat(response.status()).isEqualTo(RefundStatus.SUCCEEDED);
            assertThat(response.refundableAmount()).isEqualByComparingTo(new BigDecimal("20000"));
            assertThat(approvedPayment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        }

        @Test
        @DisplayName("PG 성공 → 전액 환불 시 REFUNDED + 주문 REFUNDED")
        void pgSuccessFullRefund() {
            Refund refund = Refund.builder()
                    .paymentId(1L)
                    .orderId(1L)
                    .idempotencyKey("payment-1-refund-1")
                    .amount(new BigDecimal("50000"))
                    .reason("전액 환불")
                    .build();
            approvedPayment.deductRefundable(new BigDecimal("50000"));

            given(refundRepository.findById(any())).willReturn(Optional.of(refund));
            given(paymentRepository.findById(1L)).willReturn(Optional.of(approvedPayment));
            given(orderRepository.findById(1L)).willReturn(Optional.of(confirmedOrder));

            PaymentCancelResult pgResult = PaymentCancelResult.builder()
                    .success(true)
                    .pgPaymentKey("pg-key-001")
                    .pgStatus("CANCELED")
                    .canceledAt(LocalDateTime.now())
                    .build();

            RefundResponse response = refundService.postRpc(refund.getId(), pgResult);

            assertThat(response.status()).isEqualTo(RefundStatus.SUCCEEDED);
            assertThat(response.refundableAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(approvedPayment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(confirmedOrder.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        }

        @Test
        @DisplayName("PG 타임아웃 → UNKNOWN (선차감 유지)")
        void pgTimeoutUnknown() {
            Refund refund = Refund.builder()
                    .paymentId(1L)
                    .orderId(1L)
                    .idempotencyKey("payment-1-refund-1")
                    .amount(new BigDecimal("30000"))
                    .reason("환불")
                    .build();
            approvedPayment.deductRefundable(new BigDecimal("30000"));

            given(refundRepository.findById(any())).willReturn(Optional.of(refund));
            given(paymentRepository.findById(1L)).willReturn(Optional.of(approvedPayment));

            PaymentCancelResult pgResult = PaymentCancelResult.builder()
                    .success(false)
                    .errorCode("PG_TIMEOUT")
                    .errorMessage("Read timed out")
                    .build();

            RefundResponse response = refundService.postRpc(refund.getId(), pgResult);

            assertThat(response.status()).isEqualTo(RefundStatus.UNKNOWN);
            // 선차감 유지 — 복원하지 않음
            assertThat(approvedPayment.getRefundableAmount()).isEqualByComparingTo(new BigDecimal("20000"));
        }

        @Test
        @DisplayName("PG 명확한 실패 → FAILED + 보상 (refundableAmount 복원)")
        void pgFailedRestore() {
            Refund refund = Refund.builder()
                    .paymentId(1L)
                    .orderId(1L)
                    .idempotencyKey("payment-1-refund-1")
                    .amount(new BigDecimal("30000"))
                    .reason("환불")
                    .build();
            approvedPayment.deductRefundable(new BigDecimal("30000"));

            given(refundRepository.findById(any())).willReturn(Optional.of(refund));
            given(paymentRepository.findById(1L)).willReturn(Optional.of(approvedPayment));

            PaymentCancelResult pgResult = PaymentCancelResult.builder()
                    .success(false)
                    .errorCode("ALREADY_CANCELED")
                    .errorMessage("이미 취소된 결제")
                    .build();

            RefundResponse response = refundService.postRpc(refund.getId(), pgResult);

            assertThat(response.status()).isEqualTo(RefundStatus.FAILED);
            // 보상 트랜잭션: 복원됨
            assertThat(approvedPayment.getRefundableAmount()).isEqualByComparingTo(new BigDecimal("50000"));
        }
    }
}
