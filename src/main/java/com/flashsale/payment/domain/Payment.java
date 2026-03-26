package com.flashsale.payment.domain;

import com.flashsale.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    // PG 공통 필드 (PG사에 독립적)
    private String pgProvider;

    private String pgPaymentKey;

    private String pgStatus;

    private String method;

    private LocalDateTime approvedAt;

    @Builder
    public Payment(Long orderId, BigDecimal totalAmount, String pgProvider) {
        this.orderId = orderId;
        this.totalAmount = totalAmount;
        this.pgProvider = pgProvider;
        this.status = PaymentStatus.PENDING;
    }

    public void approve(String pgPaymentKey, String pgStatus, String method, LocalDateTime approvedAt) {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 승인 가능: 현재 " + this.status);
        }
        this.pgPaymentKey = pgPaymentKey;
        this.pgStatus = pgStatus;
        this.method = method;
        this.approvedAt = approvedAt;
        this.status = PaymentStatus.APPROVED;
    }

    public void fail(String pgStatus) {
        this.pgStatus = pgStatus;
        this.status = PaymentStatus.FAILED;
    }

    public void cancel(String pgStatus) {
        this.pgStatus = pgStatus;
        this.status = PaymentStatus.CANCELLED;
    }

    public void updatePgStatus(String pgStatus) {
        this.pgStatus = pgStatus;
    }
}
