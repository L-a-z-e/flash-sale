package com.flashsale.reconciler.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "compensation_failure")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompensationFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long timeDealId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private boolean resolved;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public CompensationFailure(Long timeDealId, int quantity) {
        this.timeDealId = timeDealId;
        this.quantity = quantity;
        this.resolved = false;
        this.retryCount = 0;
        this.createdAt = LocalDateTime.now();
    }

    public void markResolved() {
        this.resolved = true;
    }

    public void incrementRetry() {
        this.retryCount++;
    }
}
