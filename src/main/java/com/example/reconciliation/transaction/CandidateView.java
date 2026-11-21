package com.example.reconciliation.transaction;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CandidateView(Long id, String source, String externalReference, LocalDate transactionDate,
                            BigDecimal amount, String currency, String description, String status) implements Serializable {
    static CandidateView from(TransactionQueueRow row) {
        return new CandidateView(row.getId(), row.getSource(), row.getExternalReference(), row.getTransactionDate(),
                row.getAmount(), row.getCurrency(), row.getDescription(), row.getStatus());
    }
}
