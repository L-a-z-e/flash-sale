package com.flashsale.refund.domain;

import com.flashsale.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refund", uniqueConstraints = {
        @UniqueConstraint(name = "uk_idempotency_key", columnNames = "idempotencyKey")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private BigDecimal amount;

    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

    private String pgRefundKey;

    @Column(columnDefinition = "TEXT")
    private String pgResponse;

    private String failureReason;

    private int reconcileAttempts;

    private LocalDateTime requestedAt;

    private LocalDateTime completedAt;

    @Builder
    public Refund(Long paymentId, Long orderId, String idempotencyKey,
                  BigDecimal amount, String reason) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.reason = reason;
        this.status = RefundStatus.PENDING;
        this.reconcileAttempts = 0;
        this.requestedAt = LocalDateTime.now();
    }

    public void succeed(String pgRefundKey, String pgResponse) {
        this.status = RefundStatus.SUCCEEDED;
        this.pgRefundKey = pgRefundKey;
        this.pgResponse = pgResponse;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String failureReason, String pgResponse) {
        this.status = RefundStatus.FAILED;
        this.failureReason = failureReason;
        this.pgResponse = pgResponse;
        this.completedAt = LocalDateTime.now();
    }

    public void markUnknown() {
        this.status = RefundStatus.UNKNOWN;
    }

    public void incrementReconcileAttempts() {
        this.reconcileAttempts++;
    }
}
