package com.example.reconciliation.imports;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class ImportService {
    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    private static final String TOPIC = "file-import-requests";
    private static final String INSERT_SQL = """
            INSERT INTO transactions
                (source, external_reference, transaction_date, amount, currency, description, status, import_batch_id, row_number)
            SELECT ?, ?, ?, ?, ?, ?, 'UNMATCHED', ?, ?
            WHERE NOT EXISTS (
                SELECT 1 FROM transactions t WHERE t.source = ? AND t.external_reference = ?
            )
            """;

    private final ImportRepository batches;
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, FileImportRequested> kafka;
    private final CacheManager cacheManager;
    private final List<FileParser> parsers;
    private final Path inbox;
    private final Path processing;
    private final Path archive;
    private final Path error;
    private final int chunkSize;

    public ImportService(ImportRepository batches, JdbcTemplate jdbc,
                         KafkaTemplate<String, FileImportRequested> kafka,
                         CacheManager cacheManager, List<FileParser> parsers,
                         @Value("${app.import.inbox:./inbox}") String inbox,
                         @Value("${app.import.processing:./import-processing}") String processing,
                         @Value("${app.import.archive:./import-archive}") String archive,
                         @Value("${app.import.error:./import-error}") String error,
                         @Value("${app.import.chunk-size:1000}") int chunkSize) {
        this.batches = batches;
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.cacheManager = cacheManager;
        this.parsers = parsers;
        this.inbox = Path.of(inbox).toAbsolutePath().normalize();
        this.processing = Path.of(processing).toAbsolutePath().normalize();
        this.archive = Path.of(archive).toAbsolutePath().normalize();
        this.error = Path.of(error).toAbsolutePath().normalize();
        this.chunkSize = Math.max(1, chunkSize);
    }

    public Path inbox() {
        return inbox;
    }

    @PostConstruct
    void createDirectories() throws IOException {
        for (Path directory : List.of(inbox, processing, archive, error)) {
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
            moveQuietly(source, archive);
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

    public void process(FileImportRequested event) {
        ImportBatch batch = batches.findById(event.batchId()).orElse(null);
        if (batch == null) {
            log.warn("Ignoring unknown import batch {}", event.batchId());
            return;
        }
        if (batch.getStatus() == ImportStatus.COMPLETED) {
            log.info("Ignoring completed import batch {}", batch.getId());
            return;
        }

        Path file = Path.of(event.filePath()).toAbsolutePath().normalize();
        MDC.put("batchId", String.valueOf(event.batchId()));
        MDC.put("fileName", file.getFileName().toString());
        batch.begin(file.toString());
        batches.save(batch);

        int processed = 0;
        int inserted = 0;
        int duplicates = 0;
        int rejected = 0;
        try {
            if (!Files.isRegularFile(file)) throw new IOException("File not found: " + file);
            FileParser parser = parserFor(file);
            FileSummary summary = summarize(file, parser);
            batch.setTotalRows((int) Math.min(Integer.MAX_VALUE, summary.dataRows()));
            if (summary.declaredRows() != null) {
                batch.setDeclaredRows(Math.toIntExact(summary.declaredRows()));
            }
            batches.save(batch);
            if (summary.declaredRows() != null && summary.declaredRows() != summary.dataRows()) {
                throw new IllegalArgumentException("Row count mismatch: file declares "
                        + summary.declaredRows() + " rows but contains " + summary.dataRows() + " data row(s)");
            }

            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                parser.validateHeader(reader.readLine());
                List<ParsedRow> chunk = new ArrayList<>(chunkSize);
                String line;
                long rowNumber = 0;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    rowNumber++;
                    if (summary.declaredRows() != null && rowNumber > summary.dataRows()) {
                        Long trailer = parser.parseTrailer(line);
                        if (trailer == null || !trailer.equals(summary.declaredRows())) {
                            throw new IllegalArgumentException("Invalid trailer line: " + line);
                        }
                        break;
                    }
                    try {
                        chunk.add(parser.parseRow(line, rowNumber));
                    } catch (IllegalArgumentException e) {
                        rejected++;
                        log.warn("Import {} row {} rejected: {}", batch.getId(), rowNumber, e.getMessage());
                    }
                    if (chunk.size() >= chunkSize) {
                        int added = flush(batch.getId(), chunk);
                        inserted += added;
                        duplicates += chunk.size() - added;
                        chunk.clear();
                        processed = (int) rowNumber;
                        saveProgress(batch.getId(), processed, inserted, duplicates, rejected);
                    }
                }
                if (!chunk.isEmpty()) {
                    int added = flush(batch.getId(), chunk);
                    inserted += added;
                    duplicates += chunk.size() - added;
                }
                processed = (int) Math.min(rowNumber, summary.dataRows());
            }

            ImportBatch finished = batches.findById(batch.getId()).orElseThrow();
            finished.complete(processed, inserted, duplicates, rejected, OffsetDateTime.now());
            batches.save(finished);
            try {
                evictCandidates();
            } catch (RuntimeException e) {
                log.warn("Could not clear the candidate cache: {}", e.getMessage());
            }
            moveQuietly(file, archive);
            log.info("Import {} completed: {} rows, {} inserted, {} duplicates, {} rejected",
                    finished.getId(), processed, inserted, duplicates, rejected);
        } catch (Exception e) {
            log.error("Import {} failed", batch.getId(), e);
            fail(batch.getId(), rootMessage(e));
            moveQuietly(file, error);
        } finally {
            MDC.remove("batchId");
            MDC.remove("fileName");
        }
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

    private FileParser parserFor(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return parsers.stream().filter(parser -> parser.supports(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No parser registered for " + name));
    }

    private int flush(Long batchId, List<ParsedRow> chunk) {
        List<ParsedRow> unique = new ArrayList<>(chunk.size());
        java.util.Set<String> seen = new java.util.HashSet<>(chunk.size());
        for (ParsedRow row : chunk) {
            if (seen.add(row.source().name() + '\0' + row.externalReference())) unique.add(row);
        }
        return countInserted(insert(batchId, unique));
    }

    private int[] insert(Long batchId, List<ParsedRow> rows) {
        return jdbc.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ParsedRow row = rows.get(i);
                ps.setString(1, row.source().name());
                ps.setString(2, row.externalReference());
                ps.setDate(3, Date.valueOf(row.transactionDate()));
                ps.setBigDecimal(4, row.amount());
                ps.setString(5, row.currency());
                ps.setString(6, row.description());
                ps.setLong(7, batchId);
                ps.setLong(8, row.rowNumber());
                ps.setString(9, row.source().name());
                ps.setString(10, row.externalReference());
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    private static int countInserted(int[] result) {
        int count = 0;
        for (int value : result) {
            if (value != 0) count++;
        }
        return count;
    }

    private void saveProgress(Long id, int processed, int inserted, int duplicates, int rejected) {
        batches.findById(id).ifPresent(current -> {
            current.progress(processed, inserted, duplicates, rejected);
            batches.save(current);
        });
    }

    private void fail(Long id, String message) {
        batches.findById(id).ifPresent(current -> {
            current.fail(message);
            batches.save(current);
        });
    }

    private void evictCandidates() {
        Cache cache = cacheManager.getCache("transaction-candidates");
        if (cache != null) cache.clear();
    }

    private static FileSummary summarize(Path file, FileParser parser) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            parser.validateHeader(reader.readLine());
            long dataRows = 0;
            String lastNonBlank = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                dataRows++;
                lastNonBlank = line;
            }
            if (lastNonBlank == null) return new FileSummary(0, null);
            Long declared = parser.parseTrailer(lastNonBlank);
            return declared == null
                    ? new FileSummary(dataRows, null)
                    : new FileSummary(dataRows - 1, declared);
        }
    }

    private record FileSummary(long dataRows, Long declaredRows) {}

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

    private static void moveQuietly(Path source, Path targetDirectory) {
        try {
            move(source, targetDirectory);
        } catch (RuntimeException e) {
            log.warn("Could not move {} to {}: {}", source, targetDirectory, e.getMessage());
        }
    }

    private static String rootMessage(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName() : message;
    }
}
