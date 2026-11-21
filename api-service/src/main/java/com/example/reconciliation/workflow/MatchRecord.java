package com.example.reconciliation.workflow;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "matches")
public class MatchRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "bank_transaction_id", nullable = false)
    private Long bankTransactionId;
    @Column(name = "internal_transaction_id", nullable = false)
    private Long internalTransactionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private MatchStatus status;
    @Column(nullable = false, length = 100)
    private String maker;
    @Column(length = 100)
    private String checker;
    @Column(length = 100)
    private String signer;
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected MatchRecord() {}
    public MatchRecord(Long bankTransactionId, Long internalTransactionId, String maker) {
        this.bankTransactionId = bankTransactionId;
        this.internalTransactionId = internalTransactionId;
        this.maker = maker;
        this.status = MatchStatus.SUBMITTED;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }
    public void checkedBy(String actor) { this.checker = actor; this.status = MatchStatus.CHECKED; this.updatedAt = OffsetDateTime.now(); }
    public void signedBy(String actor) { this.signer = actor; this.status = MatchStatus.SIGNED; this.updatedAt = OffsetDateTime.now(); }
    public void rejected(String actor, String reason) {
        if (this.status == MatchStatus.SUBMITTED) this.checker = actor;
        if (this.status == MatchStatus.CHECKED) this.signer = actor;
        this.status = MatchStatus.REJECTED;
        this.rejectionReason = reason;
        this.updatedAt = OffsetDateTime.now();
    }
    public Long getId() { return id; }
    public Long getBankTransactionId() { return bankTransactionId; }
    public Long getInternalTransactionId() { return internalTransactionId; }
    public MatchStatus getStatus() { return status; }
    public String getMaker() { return maker; }
    public String getChecker() { return checker; }
    public String getSigner() { return signer; }
    public String getRejectionReason() { return rejectionReason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
