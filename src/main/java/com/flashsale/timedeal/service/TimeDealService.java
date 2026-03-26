package com.flashsale.timedeal.service;

import com.flashsale.common.exception.BusinessException;
import com.flashsale.common.exception.ErrorCode;
import com.flashsale.timedeal.domain.TimeDeal;
import com.flashsale.timedeal.dto.TimeDealCreateRequest;
import com.flashsale.timedeal.dto.TimeDealResponse;
import com.flashsale.timedeal.repository.TimeDealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TimeDealService {

    private final TimeDealRepository timeDealRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public TimeDealResponse create(TimeDealCreateRequest request) {
        TimeDeal timeDeal = TimeDeal.builder()
                .productName(request.getProductName())
                .totalStock(request.getTotalStock())
                .price(request.getPrice())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .build();

        timeDealRepository.save(timeDeal);

        // Redis에 재고 초기화
        redisTemplate.opsForValue()
                .set("stock:" + timeDeal.getId(), String.valueOf(timeDeal.getTotalStock()));

        return TimeDealResponse.from(timeDeal);
    }

    @Transactional(readOnly = true)
    public TimeDealResponse getById(Long id) {
        TimeDeal timeDeal = timeDealRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TIME_DEAL_NOT_FOUND));

        return TimeDealResponse.from(timeDeal);
    }
}
