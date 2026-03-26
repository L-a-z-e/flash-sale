package com.flashsale.purchase.dto;

import com.flashsale.order.domain.OrderStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PurchaseResponse {

    private final Long orderId;
    private final OrderStatus status;
    private final String message;
}
