package com.example.reconciliation.workflow;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MatchRepository extends JpaRepository<MatchRecord, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MatchRecord m where m.id = :id")
    Optional<MatchRecord> findLockedById(@Param("id") Long id);
}
