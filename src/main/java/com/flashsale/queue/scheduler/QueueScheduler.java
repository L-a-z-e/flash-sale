package com.flashsale.queue.scheduler;

import com.flashsale.queue.service.QueueService;
import com.flashsale.timedeal.domain.TimeDeal;
import com.flashsale.timedeal.repository.TimeDealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class QueueScheduler {

    private final QueueService queueService;
    private final TimeDealRepository timeDealRepository;

    private static final int BATCH_SIZE = 100;

    @Scheduled(fixedDelay = 1000)
    public void promoteWaitingUsers() {
        // 현재 진행 중인 타임딜만 조회
        LocalDateTime now = LocalDateTime.now();
        List<TimeDeal> activeDeals = timeDealRepository.findByStartAtBeforeAndEndAtAfter(now, now);

        for (TimeDeal deal : activeDeals) {
            queueService.promote(deal.getId(), BATCH_SIZE);
        }
    }
}
