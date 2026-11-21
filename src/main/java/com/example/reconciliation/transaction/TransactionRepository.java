package com.example.reconciliation.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TransactionRepository extends JpaRepository<TransactionRecord, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TransactionRecord t where t.id = :id")
    java.util.Optional<TransactionRecord> findLockedById(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query(value = "UPDATE transactions SET status = :status WHERE id = :id", nativeQuery = true)
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Query(value = """
        SELECT id, source, external_reference AS \"externalReference\", transaction_date AS \"transactionDate\",
               amount, currency, description, status, import_batch_id AS \"importBatchId\"
        FROM transactions
        WHERE (:status IS NULL OR status = :status)
          AND (:source IS NULL OR source = :source)
          AND (CAST(:batchId AS bigint) IS NULL OR import_batch_id = :batchId)
          AND (:externalReference IS NULL OR POSITION(LOWER(:externalReference) IN LOWER(external_reference)) > 0)
          AND (CAST(:minAmount AS numeric) IS NULL OR amount >= :minAmount)
          AND (CAST(:maxAmount AS numeric) IS NULL OR amount <= :maxAmount)
          AND (:fromDate IS NULL OR transaction_date >= CAST(:fromDate AS date))
          AND (:toDate IS NULL OR transaction_date <= CAST(:toDate AS date))
        ORDER BY transaction_date DESC, id DESC
        """, countQuery = """
        SELECT count(*) FROM transactions
        WHERE (:status IS NULL OR status = :status)
          AND (:source IS NULL OR source = :source)
          AND (CAST(:batchId AS bigint) IS NULL OR import_batch_id = :batchId)
          AND (:externalReference IS NULL OR POSITION(LOWER(:externalReference) IN LOWER(external_reference)) > 0)
          AND (CAST(:minAmount AS numeric) IS NULL OR amount >= :minAmount)
          AND (CAST(:maxAmount AS numeric) IS NULL OR amount <= :maxAmount)
          AND (:fromDate IS NULL OR transaction_date >= CAST(:fromDate AS date))
          AND (:toDate IS NULL OR transaction_date <= CAST(:toDate AS date))
        """, nativeQuery = true)
    Page<TransactionQueueRow> findQueue(@Param("status") String status,
                                        @Param("source") String source,
                                        @Param("batchId") Long batchId,
                                        @Param("externalReference") String externalReference,
                                        @Param("minAmount") java.math.BigDecimal minAmount,
                                        @Param("maxAmount") java.math.BigDecimal maxAmount,
                                        @Param("fromDate") String fromDate,
                                        @Param("toDate") String toDate,
                                        Pageable pageable);

    @Query(value = """
        SELECT id, source, external_reference AS \"externalReference\", transaction_date AS \"transactionDate\",
               amount, currency, description, status, import_batch_id AS \"importBatchId\"
        FROM transactions
        WHERE source <> :source AND status = 'UNMATCHED' AND amount = :amount AND currency = :currency
          AND transaction_date BETWEEN CAST(:dateFrom AS date) AND CAST(:dateTo AS date)
        ORDER BY abs(transaction_date - CAST(:targetDate AS date)), id
        LIMIT :limit
        """, nativeQuery = true)
    java.util.List<TransactionQueueRow> findCandidates(@Param("source") String source,
            @Param("amount") java.math.BigDecimal amount, @Param("currency") String currency,
            @Param("dateFrom") String dateFrom, @Param("dateTo") String dateTo,
            @Param("targetDate") String targetDate, @Param("limit") int limit);

}
