package com.flashsale.reconciler.repository;

import com.flashsale.reconciler.domain.CompensationFailure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompensationFailureRepository extends JpaRepository<CompensationFailure, Long> {

    List<CompensationFailure> findByResolvedFalse();
}
