package com.flashsale.refund.service;

import com.flashsale.common.exception.BusinessException;
import com.flashsale.common.exception.ErrorCode;
import com.flashsale.order.domain.Order;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.payment.domain.Payment;
import com.flashsale.payment.gateway.PaymentCancelResult;
import com.flashsale.payment.gateway.PaymentGateway;
import com.flashsale.payment.repository.PaymentRepository;
import com.flashsale.refund.domain.Refund;
import com.flashsale.refund.domain.RefundStatus;
import com.flashsale.refund.dto.RefundRequest;
import com.flashsale.refund.dto.RefundResponse;
import com.flashsale.refund.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;

    /**
     * 환불 처리 — Pre-RPC / RPC / Post-RPC 분리 패턴 (Airbnb Orpheus)
     */
    public RefundResponse processRefund(RefundRequest request) {
        // Phase 1: Pre-RPC (DB 트랜잭션 — 락 획득, 검증, 선차감)
        Refund refund = preRpc(request);

        // Phase 2: RPC (트랜잭션 없음 — PG 환불 API 호출)
        PaymentCancelResult pgResult = rpc(refund);

        // Phase 3: Post-RPC (DB 트랜잭션 — 결과 반영 or 보상)
        return postRpc(refund.getId(), pgResult);
    }

    /**
     * Pre-RPC: 비관적 락으로 검증 + 선차감 + Refund PENDING 생성
     * 트랜잭션 커밋과 동시에 락 해제 → PG 호출 중 다른 환불 차단하지 않음
     */
    @Transactional
    public Refund preRpc(RefundRequest request) {
        // 1. 비관적 락으로 Payment 조회
        Payment payment = paymentRepository.findByIdForUpdate(request.paymentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        // 2. 상태 검증
        if (!payment.isRefundable()) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED);
        }

        // 3. 금액 검증
        if (request.amount().compareTo(payment.getRefundableAmount()) > 0) {
            throw new BusinessException(ErrorCode.REFUND_AMOUNT_EXCEEDED);
        }

        // 4. 멱등키 생성 + 중복 체크
        long refundSeq = refundRepository.findByPaymentId(payment.getId()).size() + 1;
        String idempotencyKey = "payment-" + payment.getId() + "-refund-" + refundSeq;

        if (refundRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            throw new BusinessException(ErrorCode.REFUND_DUPLICATE);
        }

        // 5. Refund 레코드 생성 (PENDING)
        Refund refund = Refund.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .idempotencyKey(idempotencyKey)
                .amount(request.amount())
                .reason(request.reason())
                .build();
        refundRepository.save(refund);

        // 6. 선차감 — PG 호출 전에 refundableAmount 차감
        payment.deductRefundable(request.amount());

        log.info("[Pre-RPC] 환불 준비 완료: refundId={}, paymentId={}, amount={}, 잔여={}",
                refund.getId(), payment.getId(), request.amount(), payment.getRefundableAmount());

        return refund;
    }

    /**
     * RPC: PG 환불 API 호출 (DB 접근 없음, 트랜잭션 없음)
     */
    private PaymentCancelResult rpc(Refund refund) {
        Payment payment = paymentRepository.findById(refund.getPaymentId()).orElseThrow();

        try {
            log.info("[RPC] PG 환불 요청: pgPaymentKey={}, amount={}",
                    payment.getPgPaymentKey(), refund.getAmount());

            return paymentGateway.cancel(payment.getPgPaymentKey(), refund.getReason());
        } catch (Exception e) {
            log.warn("[RPC] PG 환불 타임아웃/에러: refundId={}, error={}",
                    refund.getId(), e.getMessage());

            return PaymentCancelResult.builder()
                    .success(false)
                    .errorCode("PG_TIMEOUT")
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * Post-RPC: PG 결과에 따라 상태 확정 or 보상 트랜잭션
     */
    @Transactional
    public RefundResponse postRpc(Long refundId, PaymentCancelResult pgResult) {
        Refund refund = refundRepository.findById(refundId).orElseThrow();
        Payment payment = paymentRepository.findById(refund.getPaymentId()).orElseThrow();

        if (pgResult.isSuccess()) {
            // 성공: 상태 확정
            refund.succeed(pgResult.getPgPaymentKey(), pgResult.getPgStatus());
            payment.updateRefundStatus();

            // 전액 환불 시 주문도 REFUNDED
            if (payment.getRefundableAmount().compareTo(BigDecimal.ZERO) == 0) {
                orderRepository.findById(payment.getOrderId())
                        .ifPresent(Order::refund);
            }

            log.info("[Post-RPC] 환불 성공: refundId={}, 잔여={}", refundId, payment.getRefundableAmount());
            return RefundResponse.from(refund, payment.getRefundableAmount(), "환불이 완료되었습니다.");

        } else if ("PG_TIMEOUT".equals(pgResult.getErrorCode())) {
            // 타임아웃: UNKNOWN — 선차감 유지, Reconciler가 처리
            refund.markUnknown();

            log.warn("[Post-RPC] 환불 UNKNOWN: refundId={}, Reconciler 대상", refundId);
            return RefundResponse.from(refund, payment.getRefundableAmount(),
                    "환불 처리 중입니다. 확인 후 결과를 안내드리겠습니다.");

        } else {
            // 명확한 실패: 보상 트랜잭션 (refundableAmount 복원)
            refund.fail(pgResult.getErrorCode(), pgResult.getErrorMessage());
            payment.restoreRefundable(refund.getAmount());

            log.info("[Post-RPC] 환불 실패 + 보상: refundId={}, 복원액={}, 잔여={}",
                    refundId, refund.getAmount(), payment.getRefundableAmount());
            return RefundResponse.from(refund, payment.getRefundableAmount(),
                    "환불 처리에 실패했습니다: " + pgResult.getErrorMessage());
        }
    }

    @Transactional(readOnly = true)
    public RefundResponse getRefund(Long refundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));
        Payment payment = paymentRepository.findById(refund.getPaymentId()).orElseThrow();
        return RefundResponse.from(refund, payment.getRefundableAmount(), null);
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> getRefundsByPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        return refundRepository.findByPaymentId(paymentId).stream()
                .map(r -> RefundResponse.from(r, payment.getRefundableAmount(), null))
                .toList();
    }
}
