package com.flashsale.timedeal.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class TimeDealCreateRequest {

    private String productName;
    private int totalStock;
    private BigDecimal price;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
}
