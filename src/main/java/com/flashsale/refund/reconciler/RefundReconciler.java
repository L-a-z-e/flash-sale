package com.flashsale.refund.reconciler;

import com.flashsale.order.domain.Order;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.payment.domain.Payment;
import com.flashsale.payment.gateway.PaymentGateway;
import com.flashsale.payment.gateway.PaymentStatusResult;
import com.flashsale.payment.repository.PaymentRepository;
import com.flashsale.refund.domain.Refund;
import com.flashsale.refund.domain.RefundStatus;
import com.flashsale.refund.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundReconciler {

    private static final int MAX_RECONCILE_ATTEMPTS = 3;
    private static final int GRACE_PERIOD_MINUTES = 5;

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;

    @Scheduled(fixedDelay = 300_000) // 5분마다
    public void reconcileUnknownRefunds() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(GRACE_PERIOD_MINUTES);
        List<Refund> unknowns = refundRepository.findByStatusAndRequestedAtBefore(
                RefundStatus.UNKNOWN, cutoff);

        if (unknowns.isEmpty()) return;

        log.info("[RefundReconciler] UNKNOWN 환불 {}건 처리 시작", unknowns.size());

        for (Refund refund : unknowns) {
            try {
                reconcileSingle(refund);
            } catch (Exception e) {
                log.error("[RefundReconciler] 처리 실패: refundId={}, error={}",
                        refund.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void reconcileSingle(Refund refund) {
        Payment payment = paymentRepository.findById(refund.getPaymentId()).orElseThrow();

        try {
            // PG 상태 조회
            PaymentStatusResult pgStatus = paymentGateway.getStatus(payment.getPgPaymentKey());

            if ("CANCELED".equals(pgStatus.getPgStatus()) || "PARTIAL_CANCELED".equals(pgStatus.getPgStatus())) {
                // PG에서 환불 완료 확인 → SUCCEEDED
                refund.succeed(pgStatus.getPgPaymentKey(), pgStatus.getPgStatus());
                payment.updateRefundStatus();

                if (payment.getRefundableAmount().compareTo(BigDecimal.ZERO) == 0) {
                    orderRepository.findById(payment.getOrderId())
                            .ifPresent(Order::refund);
                }

                log.info("[RefundReconciler] UNKNOWN → SUCCEEDED: refundId={}", refund.getId());

            } else if ("DONE".equals(pgStatus.getPgStatus())) {
                // PG에서 환불 안 됨 → FAILED + 보상
                refund.fail("PG_NOT_CANCELED", "PG 상태: " + pgStatus.getPgStatus());
                payment.restoreRefundable(refund.getAmount());

                log.info("[RefundReconciler] UNKNOWN → FAILED + 복원: refundId={}, 복원액={}",
                        refund.getId(), refund.getAmount());
            }

        } catch (Exception e) {
            // PG 조회도 실패 → 재시도 카운터 증가
            refund.incrementReconcileAttempts();

            if (refund.getReconcileAttempts() >= MAX_RECONCILE_ATTEMPTS) {
                log.error("[RefundReconciler] 최대 재시도 초과! 수동 처리 필요: refundId={}, attempts={}",
                        refund.getId(), refund.getReconcileAttempts());
            } else {
                log.warn("[RefundReconciler] PG 조회 실패, 다음 주기에 재시도: refundId={}, attempts={}",
                        refund.getId(), refund.getReconcileAttempts());
            }
        }
    }
}
