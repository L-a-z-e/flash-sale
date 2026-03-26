package com.flashsale.common.config;

import com.flashsale.order.domain.OrderStatus;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.timedeal.domain.TimeDeal;
import com.flashsale.timedeal.repository.TimeDealRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisWarmUpRunner implements ApplicationRunner {

    private final TimeDealRepository timeDealRepository;
    private final OrderRepository orderRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        LocalDateTime now = LocalDateTime.now();
        List<TimeDeal> activeDeals = timeDealRepository.findByStartAtBeforeAndEndAtAfter(now, now);

        int loaded = 0;
        for (TimeDeal deal : activeDeals) {
            String redisKey = "stock:" + deal.getId();

            // SET NX: 키가 없을 때만 적재 (다중 인스턴스 안전)
            // 키가 이미 있으면 다른 Pod이나 이전 실행에서 적재된 것 → skip
            Boolean absent = redisTemplate.opsForValue().setIfAbsent(redisKey, "0");
            if (Boolean.FALSE.equals(absent)) {
                log.info("[Warm-up] 이미 존재하는 키, skip: timeDealId={}, 현재값={}",
                        deal.getId(), redisTemplate.opsForValue().get(redisKey));
                continue;
            }

            // 키가 새로 생성됨 → DB 기준으로 정확한 값 설정
            int soldQuantity = orderRepository.sumQuantityByTimeDealIdAndStatusIn(
                    deal.getId(),
                    List.of(OrderStatus.CONFIRMED, OrderStatus.PENDING)
            );
            int expectedStock = deal.getTotalStock() - soldQuantity;
            redisTemplate.opsForValue().set(redisKey, String.valueOf(expectedStock));

            log.info("[Warm-up] 재고 적재: timeDealId={}, stock={} (total={}, sold={})",
                    deal.getId(), expectedStock, deal.getTotalStock(), soldQuantity);
            loaded++;
        }

        log.info("[Warm-up] 완료: 활성 {}개 중 {}개 적재", activeDeals.size(), loaded);
    }
}
