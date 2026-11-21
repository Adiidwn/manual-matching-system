package com.example.reconciliation.imports;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Component
public class IncomingFileScheduler {
    private static final Logger log = LoggerFactory.getLogger(IncomingFileScheduler.class);

    private final ImportService service;
    private final Path inbox;

    public IncomingFileScheduler(ImportService service, @Value("${app.import.inbox:./inbox}") String inbox) {
        this.service = service;
        this.inbox = Path.of(inbox).toAbsolutePath().normalize();
    }

    @Scheduled(fixedDelayString = "${app.import.poll-interval-ms:30000}",
            initialDelayString = "${app.import.initial-delay-ms:10000}")
    public void poll() {
        int registered = scanOnce();
        if (registered > 0) log.info("Registered {} import file(s) from {}", registered, inbox);
    }

    public synchronized int scanOnce() {
        if (!Files.isDirectory(inbox)) return 0;
        try (Stream<Path> files = Files.list(inbox)) {
            List<Path> candidates = files
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                    .filter(IncomingFileScheduler::isStable)
                    .sorted()
                    .toList();
            int registered = 0;
            for (Path file : candidates) {
                try {
                    service.registerFile(file);
                    registered++;
                } catch (RuntimeException e) {
                    log.error("Could not register {}", file, e);
                }
            }
            return registered;
        } catch (IOException e) {
            log.error("Could not scan {}", inbox, e);
            return 0;
        }
    }

    /** Waits until a file has stopped changing, so a file copied into the folder is not read half-way. */
    private static boolean isStable(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis() < System.currentTimeMillis() - 3000;
        } catch (IOException e) {
            return false;
        }
    }
}
