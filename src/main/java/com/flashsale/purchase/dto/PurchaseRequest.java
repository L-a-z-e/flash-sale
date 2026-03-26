package com.flashsale.purchase.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PurchaseRequest {

    private Long timeDealId;
    private int quantity;
    private String idempotencyKey;
}
