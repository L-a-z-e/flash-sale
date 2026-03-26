package com.flashsale.timedeal.repository;

import com.flashsale.timedeal.domain.TimeDeal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TimeDealRepository extends JpaRepository<TimeDeal, Long> {

    List<TimeDeal> findByStartAtBeforeAndEndAtAfter(LocalDateTime start, LocalDateTime end);

    List<TimeDeal> findByEndAtBefore(LocalDateTime endAt);

    // DB Fallback용 atomic UPDATE
    @Modifying
    @Query("UPDATE TimeDeal t SET t.stock = t.stock - :quantity " +
            "WHERE t.id = :id AND t.stock >= :quantity")
    int deductStockAtomically(@Param("id") Long id, @Param("quantity") int quantity);
}
