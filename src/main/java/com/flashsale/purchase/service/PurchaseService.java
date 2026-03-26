package com.flashsale.purchase.service;

import com.flashsale.common.exception.BusinessException;
import com.flashsale.common.exception.ErrorCode;
import com.flashsale.order.domain.Order;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.purchase.dto.PurchaseRequest;
import com.flashsale.purchase.dto.PurchaseResponse;
import com.flashsale.timedeal.domain.TimeDeal;
import com.flashsale.outbox.domain.OutboxEvent;
import com.flashsale.outbox.dto.OrderCreatedPayload;
import com.flashsale.outbox.repository.OutboxEventRepository;
import com.flashsale.reconciler.domain.CompensationFailure;
import com.flashsale.reconciler.repository.CompensationFailureRepository;
import com.flashsale.timedeal.repository.TimeDealRepository;
import com.flashsale.timedeal.service.StockService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final RedisTemplate<String, String> redisTemplate;
    private final StockService stockService;
    private final OrderRepository orderRepository;
    private final TimeDealRepository timeDealRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final CompensationFailureRepository compensationFailureRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public PurchaseResponse purchase(Long userId, String queueToken, PurchaseRequest request) {
        // 0. 토큰 검증 (대기열을 통과했는지)
        validateQueueToken(userId, request.getTimeDealId(), queueToken);

        // 1. 멱등성 체크 (Redis 1차 방어, DB UNIQUE 2차 방어)
        try {
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent("idempotency:" + request.getIdempotencyKey(), "processing", Duration.ofMinutes(5));

            if (Boolean.FALSE.equals(isNew)) {
                Optional<Order> existingOrder = orderRepository.findByIdempotencyKey(request.getIdempotencyKey());

                if (existingOrder.isPresent()) {
                    return toResponse(existingOrder.get(), "이미 접수된 주문입니다.");
                }

                // Redis에 키는 있는데 DB에 주문 없음 → 이전 처리 실패 → 키 삭제 후 진행
                redisTemplate.delete("idempotency:" + request.getIdempotencyKey());
            }
        } catch (DataAccessException e) {
            // Redis 장애 → 멱등성 체크 건너뛰기 (DB UNIQUE 제약이 2차 방어)
            log.warn("Redis 멱등성 체크 실패, DB UNIQUE로 방어: {}", e.getMessage());
        }

        // 2. Timedeal 조회
        TimeDeal timeDeal = timeDealRepository.findById(request.getTimeDealId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TIME_DEAL_NOT_FOUND));

        // 3. 재고 차감
        stockService.deductStock(request.getTimeDealId(), request.getQuantity());

        try {
            // 4. 주문 생성
            BigDecimal totalPrice = timeDeal.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()));

            Order order = Order.builder()
                    .userId(userId)
                    .timeDealId(request.getTimeDealId())
                    .quantity(request.getQuantity())
                    .totalPrice(totalPrice)
                    .idempotencyKey(request.getIdempotencyKey())
                    .build();

            orderRepository.save(order);

            // 5. Outbox 이벤트 저장 (같은 트랜잭션)
            outboxEventRepository.save(OutboxEvent.builder()
                    .eventType("ORDER_CREATED")
                    .payload(toPayloadJson(order))
                    .build());

            return toResponse(order, "주문이 접수되었습니다.");
        } catch (Exception e) {
            // 주문 생성 실패 → Redis 재고 복원 (F1 보상)
            log.error("주문 생성 실패, 재고 복원: timeDealId={}", request.getTimeDealId(), e);
            try {
                stockService.restoreStock(request.getTimeDealId(), request.getQuantity());
            } catch (Exception restoreEx) {
                log.error("재고 복원 실패, CompensationFailure 기록: timeDealId={}",
                        request.getTimeDealId(), restoreEx);
                compensationFailureRepository.save(
                        new CompensationFailure(request.getTimeDealId(), request.getQuantity()));
            }
            throw e;
        }
    }

    private void validateQueueToken(Long userId, Long timeDealId, String queueToken) {
        if (queueToken == null || queueToken.isBlank()) {
            throw new BusinessException(ErrorCode.QUEUE_TOKEN_REQUIRED);
        }

        String tokenKey = "token:" + userId + ":" + timeDealId;
        try {
            String storedToken = redisTemplate.opsForValue().get(tokenKey);

            if (storedToken == null || !storedToken.equals(queueToken)) {
                throw new BusinessException(ErrorCode.QUEUE_TOKEN_INVALID);
            }

            // 토큰 사용 후 삭제 (1회용)
            redisTemplate.delete(tokenKey);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // Redis 장애 → 토큰 검증 skip (DB atomic UPDATE가 Over-selling 방어)
            log.warn("Redis 장애로 토큰 검증 skip: userId={}, timeDealId={}", userId, timeDealId);
        }
    }

    private PurchaseResponse toResponse(Order order, String message) {
        return PurchaseResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .message(message)
                .build();
    }

    private String toPayloadJson(Order order) {
        OrderCreatedPayload payload = OrderCreatedPayload.builder()
                .orderId(order.getId())
                .totalPrice(order.getTotalPrice())
                .idempotencyKey(order.getIdempotencyKey())
                .build();
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox payload 직렬화 실패", e);
        }
    }
}
