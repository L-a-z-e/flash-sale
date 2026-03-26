package com.flashsale.purchase.service;

import com.flashsale.common.exception.BusinessException;
import com.flashsale.common.exception.ErrorCode;
import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderStatus;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.purchase.dto.PurchaseRequest;
import com.flashsale.purchase.dto.PurchaseResponse;
import com.flashsale.timedeal.domain.TimeDeal;
import com.flashsale.timedeal.repository.TimeDealRepository;
import com.flashsale.timedeal.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @InjectMocks
    private PurchaseService purchaseService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private StockService stockService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TimeDealRepository timeDealRepository;

    private PurchaseRequest request;
    private TimeDeal timeDeal;
    private final Long userId = 42L;

    @BeforeEach
    void setUp() {
        request = new PurchaseRequest();
        setField(request, "timeDealId", 1L);
        setField(request, "quantity", 1);
        setField(request, "idempotencyKey", "test-uuid-123");

        timeDeal = TimeDeal.builder()
                .productName("테스트 상품")
                .totalStock(100)
                .price(BigDecimal.valueOf(15000))
                .startAt(LocalDateTime.now().minusHours(1))
                .endAt(LocalDateTime.now().plusHours(1))
                .build();
    }

    @Nested
    @DisplayName("정상 흐름")
    class HappyPath {

        @Test
        @DisplayName("정상 구매 → PENDING 주문 생성")
        void purchaseSuccess() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .willReturn(true);
            given(timeDealRepository.findById(1L)).willReturn(Optional.of(timeDeal));
            given(stockService.deductStock(1L, 1)).willReturn(99L);
            given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                setField(order, "id", 1L);
                return order;
            });

            PurchaseResponse response = purchaseService.purchase(userId, request);

            assertThat(response.getOrderId()).isEqualTo(1L);
            assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(response.getMessage()).contains("접수");
            verify(stockService).deductStock(1L, 1);
            verify(orderRepository).save(any(Order.class));
        }
    }

    @Nested
    @DisplayName("멱등성 체크")
    class Idempotency {

        @Test
        @DisplayName("중복 키 + DB 주문 있음 → 기존 주문 반환")
        void duplicateKeyWithExistingOrder() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .willReturn(false);

            Order existingOrder = Order.builder()
                    .userId(userId)
                    .timeDealId(1L)
                    .quantity(1)
                    .totalPrice(BigDecimal.valueOf(15000))
                    .idempotencyKey("test-uuid-123")
                    .build();
            setField(existingOrder, "id", 99L);

            given(orderRepository.findByIdempotencyKey("test-uuid-123"))
                    .willReturn(Optional.of(existingOrder));

            PurchaseResponse response = purchaseService.purchase(userId, request);

            assertThat(response.getOrderId()).isEqualTo(99L);
            assertThat(response.getMessage()).contains("이미");
            verify(stockService, never()).deductStock(anyLong(), anyInt());
        }

        @Test
        @DisplayName("중복 키 + DB 주문 없음 → 키 삭제 후 새 주문 진행")
        void duplicateKeyWithNoOrder() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .willReturn(false);
            given(orderRepository.findByIdempotencyKey("test-uuid-123"))
                    .willReturn(Optional.empty());
            given(redisTemplate.delete(anyString())).willReturn(true);

            given(timeDealRepository.findById(1L)).willReturn(Optional.of(timeDeal));
            given(stockService.deductStock(1L, 1)).willReturn(99L);
            given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                setField(order, "id", 1L);
                return order;
            });

            PurchaseResponse response = purchaseService.purchase(userId, request);

            assertThat(response.getOrderId()).isEqualTo(1L);
            verify(redisTemplate).delete("idempotency:test-uuid-123");
            verify(stockService).deductStock(1L, 1);
        }

        @Test
        @DisplayName("Redis 장애 시 멱등성 체크 건너뛰기")
        void redisFailureSkipsIdempotency() {
            given(redisTemplate.opsForValue()).willThrow(new RedisConnectionFailureException("Connection refused"));

            given(timeDealRepository.findById(1L)).willReturn(Optional.of(timeDeal));
            given(stockService.deductStock(1L, 1)).willReturn(99L);
            given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                setField(order, "id", 1L);
                return order;
            });

            PurchaseResponse response = purchaseService.purchase(userId, request);

            assertThat(response.getOrderId()).isEqualTo(1L);
            verify(stockService).deductStock(1L, 1);
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class FailureCases {

        @Test
        @DisplayName("TimeDeal 없음 → TIME_DEAL_NOT_FOUND")
        void timeDealNotFound() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .willReturn(true);
            given(timeDealRepository.findById(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> purchaseService.purchase(userId, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getErrorCode()).isEqualTo(ErrorCode.TIME_DEAL_NOT_FOUND);
                    });

            verify(stockService, never()).deductStock(anyLong(), anyInt());
        }

        @Test
        @DisplayName("재고 부족 → STOCK_INSUFFICIENT (보상 없음)")
        void stockInsufficient() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .willReturn(true);
            given(timeDealRepository.findById(1L)).willReturn(Optional.of(timeDeal));
            given(stockService.deductStock(1L, 1))
                    .willThrow(new BusinessException(ErrorCode.STOCK_INSUFFICIENT));

            assertThatThrownBy(() -> purchaseService.purchase(userId, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getErrorCode()).isEqualTo(ErrorCode.STOCK_INSUFFICIENT);
                    });

            // 재고 차감 전에 실패 → 보상 불필요
            verify(stockService, never()).restoreStock(anyLong(), anyInt());
        }

        @Test
        @DisplayName("주문 생성 실패 → restoreStock 호출")
        void orderSaveFailure_restoresStock() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .willReturn(true);
            given(timeDealRepository.findById(1L)).willReturn(Optional.of(timeDeal));
            given(stockService.deductStock(1L, 1)).willReturn(99L);
            given(orderRepository.save(any(Order.class)))
                    .willThrow(new RuntimeException("DB connection lost"));

            assertThatThrownBy(() -> purchaseService.purchase(userId, request))
                    .isInstanceOf(RuntimeException.class);

            verify(stockService).restoreStock(1L, 1);
        }
    }

    // 리플렉션으로 @NoArgsConstructor DTO, protected 필드에 값 설정
    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException e) {
            // 부모 클래스에서 찾기
            try {
                java.lang.reflect.Field field = target.getClass().getSuperclass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
