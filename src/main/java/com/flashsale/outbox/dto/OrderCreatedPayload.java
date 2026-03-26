package com.flashsale.outbox.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class OrderCreatedPayload {

    private Long orderId;
    private BigDecimal totalPrice;
    private String idempotencyKey;

    @Builder
    public OrderCreatedPayload(Long orderId, BigDecimal totalPrice, String idempotencyKey) {
        this.orderId = orderId;
        this.totalPrice = totalPrice;
        this.idempotencyKey = idempotencyKey;
    }
}
