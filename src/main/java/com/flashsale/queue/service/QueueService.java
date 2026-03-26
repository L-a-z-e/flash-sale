package com.flashsale.queue.service;

import com.flashsale.queue.dto.QueuePositionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String QUEUE_KEY_PREFIX = "queue:";
    private static final String TOKEN_KEY_PREFIX = "token:";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    /**
     * 대기열 진입.
     * ZADD로 등록하고 현재 순번을 반환한다.
     */
    public QueuePositionResponse enter(Long userId, Long timeDealId) {
        String queueKey = QUEUE_KEY_PREFIX + timeDealId;
        double score = System.currentTimeMillis();

        // ZADD: score=현재시각, member=userId → 먼저 온 사람이 앞에
        redisTemplate.opsForZSet().add(queueKey, String.valueOf(userId), score);

        // ZRANK: 0부터 시작하는 순번
        Long rank = redisTemplate.opsForZSet().rank(queueKey, String.valueOf(userId));
        long position = (rank != null) ? rank + 1 : 0;

        return QueuePositionResponse.builder()
                .position(position)
                .status("WAITING")
                .build();
    }

    /**
     * 순번 조회.
     * 입장 토큰이 있으면 READY, 없으면 현재 순번 반환.
     */
    public QueuePositionResponse getPosition(Long userId, Long timeDealId) {
        String tokenKey = TOKEN_KEY_PREFIX + userId + ":" + timeDealId;

        // 토큰이 이미 발급됐는지 확인
        String token = redisTemplate.opsForValue().get(tokenKey);
        if (token != null) {
            return QueuePositionResponse.builder()
                    .position(0)
                    .status("READY")
                    .token(token)
                    .build();
        }

        // 대기열에서 순번 조회
        String queueKey = QUEUE_KEY_PREFIX + timeDealId;
        Long rank = redisTemplate.opsForZSet().rank(queueKey, String.valueOf(userId));

        if (rank == null) {
            // 대기열에도 없고 토큰도 없음 → 진입 안 한 상태
            return QueuePositionResponse.builder()
                    .position(-1)
                    .status("NOT_IN_QUEUE")
                    .build();
        }

        return QueuePositionResponse.builder()
                .position(rank + 1)
                .status("WAITING")
                .build();
    }

    /**
     * 승격: 상위 N명에게 입장 토큰 발급 + 대기열에서 제거.
     * Scheduler가 주기적으로 호출.
     */
    public int promote(Long timeDealId, int batchSize) {
        String queueKey = QUEUE_KEY_PREFIX + timeDealId;

        // ZRANGE: 상위 N명 (score 낮은 순 = 먼저 온 순)
        Set<String> members = redisTemplate.opsForZSet().range(queueKey, 0, batchSize - 1);

        if (members == null || members.isEmpty()) {
            return 0;
        }

        int promoted = 0;
        for (String userId : members) {
            String tokenKey = TOKEN_KEY_PREFIX + userId + ":" + timeDealId;
            String tokenValue = "token-" + userId + "-" + timeDealId + "-" + System.currentTimeMillis();

            // 토큰 발급 (5분 TTL)
            redisTemplate.opsForValue().set(tokenKey, tokenValue, TOKEN_TTL);

            // 대기열에서 제거
            redisTemplate.opsForZSet().remove(queueKey, userId);

            promoted++;
        }

        if (promoted > 0) {
            log.info("대기열 승격: timeDealId={}, promoted={}", timeDealId, promoted);
        }

        return promoted;
    }
}
