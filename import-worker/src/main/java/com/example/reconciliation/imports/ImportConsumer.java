package com.example.reconciliation.imports;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ImportConsumer {
    private final ImportProcessor processor;

    public ImportConsumer(ImportProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(topics = "file-import-requests")
    public void consume(FileImportRequested request) {
        processor.process(request);
    }
}
