package com.example.reconciliation.transaction;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class CandidateMatchingService {
    private final TransactionRepository repository;

    public CandidateMatchingService(TransactionRepository repository) { this.repository = repository; }

    @Cacheable(cacheNames = "transaction-candidates", key = "#transactionId + ':' + #toleranceDays + ':' + #limit")
    public List<CandidateView> find(Long transactionId, int toleranceDays, int limit) {
        if (toleranceDays < 0 || toleranceDays > 30) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "toleranceDays must be 0..30");
        if (limit < 1 || limit > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be 1..50");
        TransactionRecord target = repository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        if (target.getStatus() != TransactionStatus.UNMATCHED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction must be UNMATCHED");
        LocalDate date = target.getTransactionDate();
        return new java.util.ArrayList<>(repository.findCandidates(target.getSource().name(), target.getAmount(), target.getCurrency(),
                        date.minusDays(toleranceDays).toString(), date.plusDays(toleranceDays).toString(), date.toString(), limit)
                .stream().map(CandidateView::from).toList());
    }
}
