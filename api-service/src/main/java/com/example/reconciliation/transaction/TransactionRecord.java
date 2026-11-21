package com.example.reconciliation.transaction;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "transactions")
public class TransactionRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private TransactionSource source;
    @Column(name = "external_reference", nullable = false, length = 100)
    private String externalReference;
    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(length = 500)
    private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private TransactionStatus status = TransactionStatus.UNMATCHED;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected TransactionRecord() {}

    public Long getId() { return id; }
    public TransactionSource getSource() { return source; }
    public String getExternalReference() { return externalReference; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDescription() { return description; }
    public TransactionStatus getStatus() { return status; }
}
