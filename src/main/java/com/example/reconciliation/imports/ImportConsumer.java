package com.example.reconciliation.imports;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ImportConsumer {
    private final ImportService service;

    public ImportConsumer(ImportService service) {
        this.service = service;
    }

    @KafkaListener(topics = "file-import-requests")
    public void consume(FileImportRequested request) {
        service.process(request);
    }
}
