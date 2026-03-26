package com.flashsale.outbox.repository;

import com.flashsale.outbox.domain.OutboxEvent;
import com.flashsale.outbox.domain.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatus(OutboxStatus status);
}
