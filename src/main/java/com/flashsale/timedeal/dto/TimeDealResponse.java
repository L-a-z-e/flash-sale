package com.flashsale.timedeal.dto;

import com.flashsale.timedeal.domain.TimeDeal;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class TimeDealResponse {

    private final Long timeDealId;
    private final String productName;
    private final int totalStock;
    private final int stock;
    private final BigDecimal price;
    private final LocalDateTime startAt;
    private final LocalDateTime endAt;

    public static TimeDealResponse from(TimeDeal timeDeal) {
        return TimeDealResponse.builder()
                .timeDealId(timeDeal.getId())
                .productName(timeDeal.getProductName())
                .totalStock(timeDeal.getTotalStock())
                .stock(timeDeal.getStock())
                .price(timeDeal.getPrice())
                .startAt(timeDeal.getStartAt())
                .endAt(timeDeal.getEndAt())
                .build();
    }
}
