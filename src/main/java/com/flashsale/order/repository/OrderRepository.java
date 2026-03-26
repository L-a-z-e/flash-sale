package com.flashsale.order.repository;

import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    // Reconciler: PENDING 상태로 일정 시간 초과된 주문 조회
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime before);

    // Reconciler: 특정 타임딜의 상태별 판매 수량 합계
    @Query("SELECT COALESCE(SUM(o.quantity), 0) FROM Order o " +
            "WHERE o.timeDealId = :timeDealId AND o.status IN :statuses")
    int sumQuantityByTimeDealIdAndStatusIn(@Param("timeDealId") Long timeDealId,
                                           @Param("statuses") List<OrderStatus> statuses);
}
