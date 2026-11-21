package com.example.reconciliation.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@Service
public class TransactionQueueService {
    private final TransactionRepository repository;

    public TransactionQueueService(TransactionRepository repository) {
        this.repository = repository;
    }

    public Page<TransactionQueueRow> getQueue(String status, String source, Long batchId,
                                               String externalReference, BigDecimal minAmount, BigDecimal maxAmount,
                                               String fromDate, String toDate, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0 and size must be 1..100");
        }
        if (externalReference != null && externalReference.trim().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "externalReference must be at most 100 characters");
        }
        if (minAmount != null && minAmount.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minAmount must be >= 0");
        }
        if (maxAmount != null && maxAmount.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxAmount must be >= 0");
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minAmount must be <= maxAmount");
        }
        return repository.findQueue(blankToNull(status), blankToNull(source), batchId,
                blankToNull(externalReference), minAmount, maxAmount,
                blankToNull(fromDate), blankToNull(toDate), PageRequest.of(page, size, Sort.unsorted()));
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
