package com.flashsale.payment.service;

import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderStatus;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.outbox.dto.OrderCreatedPayload;
import com.flashsale.payment.domain.Payment;
import com.flashsale.payment.gateway.PaymentApproveRequest;
import com.flashsale.payment.gateway.PaymentApproveResult;
import com.flashsale.payment.gateway.PaymentGateway;
import com.flashsale.payment.repository.PaymentRepository;
import com.flashsale.reconciler.domain.CompensationFailure;
import com.flashsale.reconciler.repository.CompensationFailureRepository;
import com.flashsale.timedeal.service.StockService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentConsumer {

    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final StockService stockService;
    private final CompensationFailureRepository compensationFailureRepository;

    @KafkaListener(topics = "order-events", groupId = "flash-sale")
    @Transactional
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("주문 이벤트 수신: key={}, partition={}, offset={}",
                record.key(), record.partition(), record.offset());

        try {
            OrderCreatedPayload payload = parsePayload(record.value());
            processPayment(payload);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("주문 이벤트 처리 실패: key={}", record.key(), e);
            throw e;
        }
    }

    private void processPayment(OrderCreatedPayload payload) {
        Order order = orderRepository.findById(payload.getOrderId())
                .orElseThrow(() -> new IllegalStateException(
                        "주문을 찾을 수 없음: orderId=" + payload.getOrderId()));

        // 멱등성 1차: Order 상태 체크
        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("이미 처리된 주문, 스킵: orderId={}, status={}",
                    order.getId(), order.getStatus());
            return;
        }

        // 멱등성 2차: Payment 중복 체크
        if (paymentRepository.findByOrderId(order.getId()).isPresent()) {
            log.info("이미 결제 진행된 주문, 스킵: orderId={}", order.getId());
            return;
        }

        // Payment 생성 (PENDING)
        Payment payment = Payment.builder()
                .orderId(order.getId())
                .totalAmount(payload.getTotalPrice())
                .pgProvider("MOCK_PG")
                .build();
        paymentRepository.save(payment);

        // PG 결제 요청
        PaymentApproveResult result = paymentGateway.approve(PaymentApproveRequest.builder()
                .orderId(String.valueOf(order.getId()))
                .amount(payload.getTotalPrice())
                .orderName("타임딜 주문 #" + order.getId())
                .idempotencyKey(payload.getIdempotencyKey())
                .build());

        if (result.isSuccess()) {
            payment.approve(
                    result.getPgPaymentKey(),
                    result.getPgStatus(),
                    result.getMethod(),
                    result.getApprovedAt()
            );
            order.confirm();
            log.info("결제 성공: orderId={}, pgPaymentKey={}",
                    order.getId(), result.getPgPaymentKey());
        } else if ("PG_CONNECTION_ERROR".equals(result.getErrorCode())) {
            // 타임아웃: PG 처리 여부 불명 → PENDING 유지 → Reconciler가 나중에 확인
            log.warn("PG 타임아웃, PENDING 유지: orderId={}, error={}",
                    order.getId(), result.getErrorMessage());
        } else {
            // PG 명시적 거절: 결제 안 됨이 확실 → 취소 + 재고 복원
            payment.fail(result.getErrorCode());
            order.cancel();
            try {
                stockService.restoreStock(order.getTimeDealId(), order.getQuantity());
            } catch (Exception restoreEx) {
                log.error("Consumer 재고 복원 실패, CompensationFailure 기록: orderId={}",
                        order.getId(), restoreEx);
                compensationFailureRepository.save(
                        new CompensationFailure(order.getTimeDealId(), order.getQuantity()));
            }
            log.warn("결제 실패: orderId={}, error={} {}",
                    order.getId(), result.getErrorCode(), result.getErrorMessage());
        }
    }

    private OrderCreatedPayload parsePayload(String json) {
        try {
            return objectMapper.readValue(json, OrderCreatedPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox payload 역직렬화 실패", e);
        }
    }
}
