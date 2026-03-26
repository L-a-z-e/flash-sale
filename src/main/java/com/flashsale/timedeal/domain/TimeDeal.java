package com.flashsale.timedeal.domain;

import com.flashsale.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "time_deal")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimeDeal extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private int totalStock;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Builder
    public TimeDeal(String productName, int totalStock, BigDecimal price,
                    LocalDateTime startAt, LocalDateTime endAt) {
        this.productName = productName;
        this.totalStock = totalStock;
        this.stock = totalStock;
        this.price = price;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public boolean isActive() {
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(startAt) && now.isBefore(endAt);
    }

    public void deductStock(int quantity) {
        if (this.stock < quantity) {
            throw new IllegalStateException("재고 부족: 남은 재고 " + this.stock);
        }
        this.stock -= quantity;
    }

    public void restoreStock(int quantity) {
        this.stock += quantity;
    }
}
