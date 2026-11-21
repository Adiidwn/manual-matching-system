package com.example.reconciliation.workflow;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "match_id", nullable = false)
    private Long matchId;
    @Column(nullable = false, length = 100)
    private String actor;
    @Column(nullable = false, length = 24)
    private String role;
    @Column(nullable = false, length = 40)
    private String action;
    @Column(length = 500)
    private String note;
    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    protected AuditEvent() {}
    public AuditEvent(Long matchId, String actor, String role, String action, String note) {
        this.matchId = matchId;
        this.actor = actor;
        this.role = role;
        this.action = action;
        this.note = note;
        this.occurredAt = OffsetDateTime.now();
    }
    public Long getId() { return id; }
    public Long getMatchId() { return matchId; }
    public String getActor() { return actor; }
    public String getRole() { return role; }
    public String getAction() { return action; }
    public String getNote() { return note; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
}
