package com.flashsale.timedeal.service;

import com.flashsale.timedeal.repository.TimeDealRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStockClient {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<Long> deductStockScript;
    private final TimeDealRepository timeDealRepository;

    @CircuitBreaker(name = "redis", fallbackMethod = "deductStockFallback")
    public Long deductStock(Long timeDealId, int quantity) {
        return redisTemplate.execute(
                deductStockScript,
                List.of("stock:" + timeDealId),
                String.valueOf(quantity)
        );
    }

    @Transactional
    public Long deductStockFallback(Long timeDealId, int quantity, Throwable e) {
        log.warn("Redis 장애, DB Fallback 실행: timeDealId={}, error={}", timeDealId, e.getMessage());
        int affected = timeDealRepository.deductStockAtomically(timeDealId, quantity);
        if (affected == 0) {
            return -1L;
        }
        return (long) affected;
    }
}
