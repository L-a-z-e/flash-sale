package com.flashsale.reconciler;

import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderStatus;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.payment.domain.Payment;
import com.flashsale.payment.gateway.PaymentGateway;
import com.flashsale.payment.gateway.PaymentStatusResult;
import com.flashsale.payment.repository.PaymentRepository;
import com.flashsale.reconciler.domain.CompensationFailure;
import com.flashsale.reconciler.repository.CompensationFailureRepository;
import com.flashsale.timedeal.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PendingOrderReconcileService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final StockService stockService;
    private final CompensationFailureRepository compensationFailureRepository;

    @Transactional
    public void reconcileOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        Optional<Payment> paymentOpt = paymentRepository.findByOrderId(orderId);

        if (paymentOpt.isEmpty()) {
            order.cancel();
            log.info("[PendingReconciler] Payment 없음 → 주문 취소: orderId={}", orderId);
            safeRestoreStock(order);
            return;
        }

        Payment payment = paymentOpt.get();

        PaymentStatusResult pgStatus;
        if (payment.getPgPaymentKey() != null) {
            pgStatus = paymentGateway.getStatus(payment.getPgPaymentKey());
        } else {
            pgStatus = paymentGateway.getStatusByOrderId(String.valueOf(orderId));
        }

        if ("DONE".equals(pgStatus.getPgStatus())) {
            payment.approve(pgStatus.getPgPaymentKey(), pgStatus.getPgStatus(),
                    payment.getMethod(), LocalDateTime.now());
            order.confirm();
            log.info("[PendingReconciler] PG 결제 확인 → 주문 확정: orderId={}, pgKey={}",
                    orderId, pgStatus.getPgPaymentKey());
        } else {
            payment.fail(pgStatus.getPgStatus());
            order.cancel();
            log.info("[PendingReconciler] PG 미완료({}) → 주문 취소: orderId={}",
                    pgStatus.getPgStatus(), orderId);
            safeRestoreStock(order);
        }
    }

    private void safeRestoreStock(Order order) {
        try {
            stockService.restoreStock(order.getTimeDealId(), order.getQuantity());
            log.info("[PendingReconciler] 재고 복원: orderId={}", order.getId());
        } catch (Exception e) {
            log.error("[PendingReconciler] 재고 복원 실패, CompensationFailure 기록: orderId={}",
                    order.getId(), e);
            compensationFailureRepository.save(
                    new CompensationFailure(order.getTimeDealId(), order.getQuantity()));
        }
    }
}
