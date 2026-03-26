package com.flashsale.reconciler;

import com.flashsale.reconciler.domain.CompensationFailure;
import com.flashsale.reconciler.repository.CompensationFailureRepository;
import com.flashsale.timedeal.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompensationRetryScheduler {

    private final CompensationFailureRepository compensationFailureRepository;
    private final StockService stockService;

    @Scheduled(fixedDelay = 10_000) // 10초
    @Transactional
    public void retryFailedCompensations() {
        List<CompensationFailure> failures = compensationFailureRepository.findByResolvedFalse();

        for (CompensationFailure failure : failures) {
            try {
                stockService.restoreStock(failure.getTimeDealId(), failure.getQuantity());
                failure.markResolved();
                log.info("[CompensationRetry] 보상 성공: timeDealId={}, quantity={}",
                        failure.getTimeDealId(), failure.getQuantity());
            } catch (Exception e) {
                failure.incrementRetry();
                log.warn("[CompensationRetry] 보상 재시도 실패: id={}, retryCount={}, timeDealId={}",
                        failure.getId(), failure.getRetryCount(), failure.getTimeDealId(), e);
            }
        }
    }
}
