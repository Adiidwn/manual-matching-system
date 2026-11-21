package com.example.reconciliation.search;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.LocalDate;

@Document(indexName = "transactions")
public class TransactionDocument {
    @Id
    private String id;
    @Field(type = FieldType.Keyword)
    private String source;
    @Field(type = FieldType.Text)
    private String externalReference;
    @Field(type = FieldType.Date, pattern = {"uuuu-MM-dd"})
    private LocalDate transactionDate;
    @Field(type = FieldType.Double)
    private BigDecimal amount;
    @Field(type = FieldType.Keyword)
    private String currency;
    @Field(type = FieldType.Text)
    private String description;

    protected TransactionDocument() {}
    public TransactionDocument(String id, String source, String externalReference, LocalDate transactionDate,
                               BigDecimal amount, String currency, String description) {
        this.id = id;
        this.source = source;
        this.externalReference = externalReference;
        this.transactionDate = transactionDate;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
    }
    public String getId() { return id; }
    public String getSource() { return source; }
    public String getExternalReference() { return externalReference; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDescription() { return description; }
}
