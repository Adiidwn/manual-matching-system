package com.example.reconciliation.workflow;

import com.example.reconciliation.transaction.TransactionRecord;
import com.example.reconciliation.transaction.TransactionRepository;
import com.example.reconciliation.transaction.TransactionSource;
import com.example.reconciliation.transaction.TransactionStatus;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static com.example.reconciliation.workflow.MatchDtos.*;

@Service
public class MatchingService {
    private final MatchRepository matches;
    private final AuditRepository audits;
    private final TransactionRepository transactions;

    public MatchingService(MatchRepository matches, AuditRepository audits, TransactionRepository transactions) {
        this.matches = matches;
        this.audits = audits;
        this.transactions = transactions;
    }

    @Transactional
    @CacheEvict(cacheNames = "transaction-candidates", allEntries = true)
    public MatchView create(CreateRequest request, String actor) {
        TransactionRecord bank = transactions.findById(request.bankTransactionId())
                .orElseThrow(() -> notFound("Bank transaction not found"));
        TransactionRecord internal = transactions.findById(request.internalTransactionId())
                .orElseThrow(() -> notFound("Internal transaction not found"));
        if (bank.getSource() != TransactionSource.BANK || internal.getSource() != TransactionSource.INTERNAL)
            throw badRequest("Select one BANK transaction and one INTERNAL transaction");
        if (bank.getStatus() != TransactionStatus.UNMATCHED || internal.getStatus() != TransactionStatus.UNMATCHED)
            throw conflict("Both transactions must be UNMATCHED");
        if (!bank.getAmount().equals(internal.getAmount()) || !bank.getCurrency().equals(internal.getCurrency()))
            throw badRequest("Amount and currency must match for a manual match");

        MatchRecord match = matches.save(new MatchRecord(bank.getId(), internal.getId(), actor));
        transactions.updateStatus(bank.getId(), TransactionStatus.PENDING_APPROVAL.name());
        transactions.updateStatus(internal.getId(), TransactionStatus.PENDING_APPROVAL.name());
        audits.save(new AuditEvent(match.getId(), actor, "MAKER", "SUBMIT", null));
        return MatchView.from(match);
    }

    @Transactional
    @CacheEvict(cacheNames = "transaction-candidates", allEntries = true)
    public MatchView checkerDecision(Long id, String actor, DecisionRequest request) {
        MatchRecord match = findLocked(id);
        requireStatus(match, MatchStatus.SUBMITTED);
        requireDifferentActor(match.getMaker(), actor, "Checker cannot approve their own submission");
        if (request.approve()) match.checkedBy(actor);
        else reject(match, actor, request.reason());
        matches.save(match);
        audits.save(new AuditEvent(id, actor, "CHECKER", request.approve() ? "APPROVE" : "REJECT", request.reason()));
        return MatchView.from(match);
    }

    @Transactional
    @CacheEvict(cacheNames = "transaction-candidates", allEntries = true)
    public MatchView signerDecision(Long id, String actor, DecisionRequest request) {
        MatchRecord match = findLocked(id);
        requireStatus(match, MatchStatus.CHECKED);
        requireDifferentActor(match.getMaker(), actor, "Signer cannot be the maker");
        requireDifferentActor(match.getChecker(), actor, "Signer cannot be the checker");
        if (request.approve()) {
            match.signedBy(actor);
            transactions.updateStatus(match.getBankTransactionId(), TransactionStatus.RECONCILED.name());
            transactions.updateStatus(match.getInternalTransactionId(), TransactionStatus.RECONCILED.name());
        } else reject(match, actor, request.reason());
        matches.save(match);
        audits.save(new AuditEvent(id, actor, "SIGNER", request.approve() ? "APPROVE" : "REJECT", request.reason()));
        return MatchView.from(match);
    }

    @Transactional
    public MatchDetails get(Long id) {
        MatchRecord match = matches.findById(id).orElseThrow(() -> notFound("Match not found"));
        return new MatchDetails(MatchView.from(match), audits.findByMatchIdOrderByOccurredAtAscIdAsc(id).stream().map(AuditView::from).toList());
    }

    private void reject(MatchRecord match, String actor, String reason) {
        if (reason == null || reason.isBlank()) throw badRequest("A rejection reason is required");
        match.rejected(actor, reason.trim());
        transactions.updateStatus(match.getBankTransactionId(), TransactionStatus.UNMATCHED.name());
        transactions.updateStatus(match.getInternalTransactionId(), TransactionStatus.UNMATCHED.name());
    }
    private MatchRecord findLocked(Long id) { return matches.findLockedById(id).orElseThrow(() -> notFound("Match not found")); }
    private void requireStatus(MatchRecord match, MatchStatus expected) {
        if (match.getStatus() != expected) throw conflict("Match must be " + expected + " but is " + match.getStatus());
    }
    private void requireDifferentActor(String previous, String actor, String message) {
        if (previous != null && previous.equalsIgnoreCase(actor)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
