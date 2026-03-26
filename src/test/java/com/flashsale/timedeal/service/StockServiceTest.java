package com.flashsale.timedeal.service;

import com.flashsale.common.exception.BusinessException;
import com.flashsale.common.exception.ErrorCode;
import com.flashsale.timedeal.repository.TimeDealRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @InjectMocks
    private StockService stockService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private RedisScript<Long> deductStockScript;

    @Mock
    private TimeDealRepository timeDealRepository;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Nested
    @DisplayName("deductStock - Redis 정상")
    class DeductStock {

        @Test
        @DisplayName("재고 10개에서 1개 차감 → 9 반환")
        void success() {
            given(redisTemplate.execute(eq(deductStockScript), anyList(), any()))
                    .willReturn(9L);

            long result = stockService.deductStock(1L, 1);

            assertThat(result).isEqualTo(9L);
        }

        @Test
        @DisplayName("재고 1개에서 1개 차감 → 0 반환 (경계값)")
        void successBoundary() {
            given(redisTemplate.execute(eq(deductStockScript), anyList(), any()))
                    .willReturn(0L);

            long result = stockService.deductStock(1L, 1);

            assertThat(result).isEqualTo(0L);
        }

        @Test
        @DisplayName("재고 부족 → STOCK_INSUFFICIENT")
        void insufficientStock() {
            given(redisTemplate.execute(eq(deductStockScript), anyList(), any()))
                    .willReturn(-1L);

            assertThatThrownBy(() -> stockService.deductStock(1L, 1))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getErrorCode()).isEqualTo(ErrorCode.STOCK_INSUFFICIENT);
                    });
        }

        @Test
        @DisplayName("키 없음 (null) → STOCK_NOT_FOUND")
        void keyNotFound_null() {
            given(redisTemplate.execute(eq(deductStockScript), anyList(), any()))
                    .willReturn(null);

            assertThatThrownBy(() -> stockService.deductStock(1L, 1))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getErrorCode()).isEqualTo(ErrorCode.STOCK_NOT_FOUND);
                    });
        }

        @Test
        @DisplayName("키 없음 (-2) → STOCK_NOT_FOUND")
        void keyNotFound_negative2() {
            given(redisTemplate.execute(eq(deductStockScript), anyList(), any()))
                    .willReturn(-2L);

            assertThatThrownBy(() -> stockService.deductStock(1L, 1))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getErrorCode()).isEqualTo(ErrorCode.STOCK_NOT_FOUND);
                    });
        }
    }

    @Nested
    @DisplayName("executeRedisScriptFallback - DB Fallback")
    class Fallback {

        @Test
        @DisplayName("DB 차감 성공 → affected rows 반환")
        void fallbackSuccess() {
            given(timeDealRepository.deductStockAtomically(1L, 1))
                    .willReturn(1);

            Long result = stockService.executeRedisScriptFallback(1L, 1, new RuntimeException("Redis down"));

            assertThat(result).isEqualTo(1L);
        }

        @Test
        @DisplayName("DB 재고 부족 → -1 반환 (handleResult에서 예외)")
        void fallbackInsufficientStock() {
            given(timeDealRepository.deductStockAtomically(1L, 1))
                    .willReturn(0);

            Long result = stockService.executeRedisScriptFallback(1L, 1, new RuntimeException("Redis down"));

            assertThat(result).isEqualTo(-1L);
        }
    }

    @Nested
    @DisplayName("restoreStock - 재고 복원")
    class RestoreStock {

        @Test
        @DisplayName("Redis INCR 호출 확인")
        void restoreSuccess() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);

            stockService.restoreStock(1L, 1);

            verify(valueOperations).increment("stock:1", 1);
        }
    }
}
