package com.example.reconciliation.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/search/transactions")
public class TransactionSearchController {
    private final TransactionDocumentRepository documents;
    private final TransactionIndexService indexService;

    public TransactionSearchController(TransactionDocumentRepository documents, TransactionIndexService indexService) {
        this.documents = documents;
        this.indexService = indexService;
    }

    @GetMapping
    public Page<TransactionDocument> search(@RequestParam String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (q.isBlank() || q.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q must contain 1..100 characters");
        if (page < 0 || size < 1 || size > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0 and size must be 1..50");
        return documents.searchText(q.trim(), PageRequest.of(page, size));
    }

    @PostMapping("/reindex")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransactionIndexService.IndexState reindex() {
        try { return indexService.startReindex(); }
        catch (IllegalStateException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage()); }
    }

    @GetMapping("/reindex")
    public TransactionIndexService.IndexState reindexStatus() { return indexService.getState(); }
}
