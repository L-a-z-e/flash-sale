package com.flashsale.reconciler;

import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderStatus;
import com.flashsale.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingOrderReconciler {

    private final OrderRepository orderRepository;
    private final PendingOrderReconcileService reconcileService;

    @Scheduled(fixedDelay = 300_000) // 5분
    public void reconcile() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
        List<Order> stalePendingOrders = orderRepository.findByStatusAndCreatedAtBefore(
                OrderStatus.PENDING, threshold);

        for (Order order : stalePendingOrders) {
            try {
                reconcileService.reconcileOrder(order.getId());
            } catch (Exception e) {
                log.error("[PendingReconciler] 처리 실패: orderId={}", order.getId(), e);
            }
        }
    }
}
