package com.example.reconciliation.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class TransactionQueueService {
    private final TransactionRepository repository;

    public TransactionQueueService(TransactionRepository repository) {
        this.repository = repository;
    }

    public Page<TransactionQueueRow> getQueue(String status, String source, String fromDate, String toDate,
                                               int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page must be >= 0 and size must be 1..100");
        return repository.findQueue(blankToNull(status), blankToNull(source), blankToNull(fromDate), blankToNull(toDate),
                PageRequest.of(page, size, Sort.unsorted()));
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
}
