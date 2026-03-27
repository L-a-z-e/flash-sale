package com.flashsale.refund.repository;

import com.flashsale.refund.domain.Refund;
import com.flashsale.refund.domain.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    List<Refund> findByPaymentId(Long paymentId);

    List<Refund> findByStatusAndRequestedAtBefore(RefundStatus status, LocalDateTime before);
}
