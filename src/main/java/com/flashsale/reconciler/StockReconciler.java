package com.flashsale.reconciler;

import com.flashsale.order.domain.OrderStatus;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.timedeal.domain.TimeDeal;
import com.flashsale.timedeal.repository.TimeDealRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockReconciler {

    private final TimeDealRepository timeDealRepository;
    private final OrderRepository orderRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Scheduled(fixedDelay = 300_000) // 5분
    public void reconcile() {
        LocalDateTime now = LocalDateTime.now();

        // 종료된 타임딜: DB 기준 강제 보정 (주문 안 들어오므로 안전)
        List<TimeDeal> endedDeals = timeDealRepository.findByEndAtBefore(now);
        for (TimeDeal deal : endedDeals) {
            try {
                forceCorrect(deal);
            } catch (Exception e) {
                log.error("[Reconciler] 종료 타임딜 보정 실패: timeDealId={}", deal.getId(), e);
            }
        }

        // 활성 타임딜: 모니터링만 (자동 보정 안 함)
        List<TimeDeal> activeDeals = timeDealRepository.findByStartAtBeforeAndEndAtAfter(now, now);
        for (TimeDeal deal : activeDeals) {
            try {
                monitor(deal);
            } catch (Exception e) {
                log.error("[Reconciler] 활성 타임딜 모니터링 실패: timeDealId={}", deal.getId(), e);
            }
        }
    }

    private void forceCorrect(TimeDeal deal) {
        StockSnapshot snapshot = snapshot(deal);
        if (!snapshot.matched()) {
            String redisKey = "stock:" + deal.getId();
            redisTemplate.opsForValue().set(redisKey, String.valueOf(snapshot.expectedStock()));

            log.info("[Reconciler] 종료 타임딜 보정: timeDealId={}, redis {} → {}",
                    deal.getId(), snapshot.redisStock(), snapshot.expectedStock());
        }
    }

    private void monitor(TimeDeal deal) {
        StockSnapshot snapshot = snapshot(deal);
        int drift = snapshot.redisStock() - snapshot.expectedStock();
        if (drift != 0) {
            log.warn("[Reconciler] 활성 타임딜 불일치 감지: timeDealId={}, redis={}, expected={}, drift={}",
                    deal.getId(), snapshot.redisStock(), snapshot.expectedStock(), drift);
        }
    }

    private StockSnapshot snapshot(TimeDeal deal) {
        int soldQuantity = orderRepository.sumQuantityByTimeDealIdAndStatusIn(
                deal.getId(),
                List.of(OrderStatus.CONFIRMED, OrderStatus.PENDING)
        );
        int expectedStock = deal.getTotalStock() - soldQuantity;

        String redisValue = redisTemplate.opsForValue().get("stock:" + deal.getId());
        int redisStock = redisValue != null ? Integer.parseInt(redisValue) : 0;

        return new StockSnapshot(soldQuantity, expectedStock, redisStock);
    }

    private record StockSnapshot(int soldQuantity, int expectedStock, int redisStock) {
        boolean matched() {
            return redisStock == expectedStock;
        }
    }
}
