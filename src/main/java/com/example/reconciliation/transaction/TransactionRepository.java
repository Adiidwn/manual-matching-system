package com.example.reconciliation.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<TransactionRecord, Long> {
    @Query(value = """
        SELECT id, source, external_reference AS \"externalReference\", transaction_date AS \"transactionDate\",
               amount, currency, description, status
        FROM transactions
        WHERE (:status IS NULL OR status = :status)
          AND (:source IS NULL OR source = :source)
          AND (:fromDate IS NULL OR transaction_date >= CAST(:fromDate AS date))
          AND (:toDate IS NULL OR transaction_date <= CAST(:toDate AS date))
        ORDER BY transaction_date DESC, id DESC
        """, countQuery = """
        SELECT count(*) FROM transactions
        WHERE (:status IS NULL OR status = :status)
          AND (:source IS NULL OR source = :source)
          AND (:fromDate IS NULL OR transaction_date >= CAST(:fromDate AS date))
          AND (:toDate IS NULL OR transaction_date <= CAST(:toDate AS date))
        """, nativeQuery = true)
    Page<TransactionQueueRow> findQueue(@Param("status") String status,
                                        @Param("source") String source,
                                        @Param("fromDate") String fromDate,
                                        @Param("toDate") String toDate,
                                        Pageable pageable);

    @Query(value = """
        SELECT id, source, external_reference AS \"externalReference\", transaction_date AS \"transactionDate\",
               amount, currency, description, status
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
