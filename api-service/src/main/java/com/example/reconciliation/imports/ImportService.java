package com.example.reconciliation.imports;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * API-side import intake: register a file once, move it to the processing folder
 * and hand it to the worker through Kafka. Parsing is not done here.
 */
@Service
public class ImportService {
    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    private static final String TOPIC = "file-import-requests";

    private final ImportRepository batches;
    private final KafkaTemplate<String, FileImportRequested> kafka;
    private final Path inbox;
    private final Path processing;

    public ImportService(ImportRepository batches, KafkaTemplate<String, FileImportRequested> kafka,
                         @Value("${app.import.inbox:./inbox}") String inbox,
                         @Value("${app.import.processing:./import-processing}") String processing) {
        this.batches = batches;
        this.kafka = kafka;
        this.inbox = Path.of(inbox).toAbsolutePath().normalize();
        this.processing = Path.of(processing).toAbsolutePath().normalize();
    }

    public Path inbox() {
        return inbox;
    }

    @PostConstruct
    void createDirectories() throws IOException {
        for (Path directory : List.of(inbox, processing)) {
            Files.createDirectories(directory);
        }
    }

    /**
     * Registers a file once. The SHA-256 hash makes re-delivery of the same file harmless:
     * completed or in-flight batches are skipped, failed batches are retried.
     */
    public synchronized ImportBatch registerFile(Path file) {
        Path source = file.toAbsolutePath().normalize();
        String hash;
        try {
            hash = sha256(source);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + source, e);
        }

        ImportBatch existing = batches.findByFileHash(hash).orElse(null);
        if (existing != null && (existing.getStatus() == ImportStatus.COMPLETED
                || existing.getStatus() == ImportStatus.PROCESSING)) {
            log.info("Skipping {} because batch {} is already {}", source.getFileName(), existing.getId(), existing.getStatus());
            deleteQuietly(source);
            return existing;
        }

        Path moved = move(source, processing);
        ImportBatch batch;
        if (existing == null) {
            batch = batches.save(new ImportBatch(source.getFileName().toString(), hash, moved.toString()));
        } else {
            existing.reset(source.getFileName().toString(), moved.toString());
            batch = batches.save(existing);
        }
        publish(batch);
        return batch;
    }

    private void publish(ImportBatch batch) {
        FileImportRequested event = new FileImportRequested(batch.getId(), batch.getFilePath());
        try {
            kafka.send(TOPIC, event).whenComplete((result, failure) -> {
                if (failure != null) {
                    log.error("Could not publish import batch {}", batch.getId(), failure);
                    fail(batch.getId(), "Kafka publish failed: " + failure.getMessage());
                }
            });
        } catch (RuntimeException e) {
            log.error("Could not publish import batch {}", batch.getId(), e);
            fail(batch.getId(), "Kafka publish failed: " + e.getMessage());
        }
    }

    private void fail(Long id, String message) {
        batches.findById(id).ifPresent(current -> {
            current.fail(message);
            batches.save(current);
        });
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
             DigestInputStream digest = new DigestInputStream(in, MessageDigest.getInstance("SHA-256"))) {
            byte[] buffer = new byte[8192];
            while (digest.read(buffer) != -1) {
                // consume the stream to feed the digest
            }
            return HexFormat.of().formatHex(digest.getMessageDigest().digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static Path move(Path source, Path targetDirectory) {
        try {
            Files.createDirectories(targetDirectory);
            Path target = targetDirectory.resolve(System.nanoTime() + "-" + source.getFileName());
            return Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not move " + source + " to " + targetDirectory, e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete duplicate upload {}: {}", file, e.getMessage());
        }
    }
}
