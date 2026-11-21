package com.example.reconciliation.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class MatchDtos {
    private MatchDtos() {}
    public record CreateRequest(@NotNull @Positive Long bankTransactionId,
                                @NotNull @Positive Long internalTransactionId) {}
    public record DecisionRequest(boolean approve, @Size(max = 500) String reason) {}
    public record MatchView(Long id, Long bankTransactionId, Long internalTransactionId, MatchStatus status,
                            String maker, String checker, String signer, String rejectionReason,
                            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        static MatchView from(MatchRecord m) {
            return new MatchView(m.getId(), m.getBankTransactionId(), m.getInternalTransactionId(), m.getStatus(),
                    m.getMaker(), m.getChecker(), m.getSigner(), m.getRejectionReason(), m.getCreatedAt(), m.getUpdatedAt());
        }
    }
    public record AuditView(Long id, String actor, String role, String action, String note, OffsetDateTime occurredAt) {
        static AuditView from(AuditEvent e) { return new AuditView(e.getId(), e.getActor(), e.getRole(), e.getAction(), e.getNote(), e.getOccurredAt()); }
    }
    public record MatchDetails(MatchView match, List<AuditView> audit) {}
}
