package com.example.reconciliation.demo;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DemoImportConsumer {
    private final DemoDataService service;
    public DemoImportConsumer(DemoDataService service) { this.service = service; }

    @KafkaListener(topics = "demo-import-requests")
    public void consume(DemoImportRequested request) {
        service.generate(request.count());
    }
}
