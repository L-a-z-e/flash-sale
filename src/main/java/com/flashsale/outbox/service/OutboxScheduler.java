package com.flashsale.outbox.service;

import com.flashsale.outbox.domain.OutboxEvent;
import com.flashsale.outbox.domain.OutboxStatus;
import com.flashsale.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private static final String TOPIC = "order-events";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findByStatus(OutboxStatus.PENDING);

        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(TOPIC, String.valueOf(event.getId()), event.getPayload()).get();
                event.markPublished();
                log.debug("Outbox 이벤트 발행 성공: id={}, type={}", event.getId(), event.getEventType());
            } catch (Exception e) {
                event.markFailed();
                log.error("Outbox 이벤트 발행 실패: id={}, retryCount={}", event.getId(), event.getRetryCount(), e);
            }
        }
    }
}
