package com.flashsale.timedeal.service;

import com.flashsale.common.exception.BusinessException;
import com.flashsale.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final RedisStockClient redisStockClient;
    private final RedisTemplate<String, String> redisTemplate;

    public long deductStock(Long timeDealId, int quantity) {
        Long result = redisStockClient.deductStock(timeDealId, quantity);

        if (result == null || result == -2) {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND);
        }
        if (result == -1) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
        }
        return result;
    }

    public void restoreStock(Long timeDealId, int quantity) {
        redisTemplate.opsForValue().increment("stock:" + timeDealId, quantity);
        log.info("Redis 재고 복원: timeDealId={}, qty={}", timeDealId, quantity);
    }
}
