package com.example.reconciliation.transaction;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionQueueService queueService;

    public TransactionController(TransactionQueueService queueService) { this.queueService = queueService; }

    @GetMapping
    public Page<TransactionQueueRow> getQueue(
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TransactionSource source,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return queueService.getQueue(status == null ? null : status.name(), source == null ? null : source.name(),
                fromDate == null ? null : fromDate.toString(), toDate == null ? null : toDate.toString(), page, size);
    }
}
