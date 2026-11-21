package com.example.reconciliation.imports;

import com.example.reconciliation.transaction.TransactionSource;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParsedRow(long rowNumber, TransactionSource source, String externalReference,
                        LocalDate transactionDate, BigDecimal amount, String currency, String description) {
}
