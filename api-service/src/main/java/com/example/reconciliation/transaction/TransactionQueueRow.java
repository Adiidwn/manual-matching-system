package com.example.reconciliation.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface TransactionQueueRow {
    Long getId();
    String getSource();
    String getExternalReference();
    LocalDate getTransactionDate();
    BigDecimal getAmount();
    String getCurrency();
    String getDescription();
    String getStatus();
    Long getImportBatchId();
}
