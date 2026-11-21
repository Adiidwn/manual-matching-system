package com.example.reconciliation.imports;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {
    private static final String HEADER = "source,external_reference,transaction_date,amount,currency,description";

    @Mock
    private ImportRepository batches;
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private KafkaTemplate<String, FileImportRequested> kafka;
    @Mock
    private CacheManager cacheManager;

    @TempDir
    Path tempDir;

    private ImportService service;

    @BeforeEach
    void setUp() {
        service = new ImportService(
                batches, jdbc, kafka, cacheManager,
                List.of(new CsvFileParser()),
                tempDir.resolve("inbox").toString(),
                tempDir.resolve("processing").toString(),
                tempDir.resolve("archive").toString(),
                tempDir.resolve("error").toString(),
                1000);
        lenient().when(batches.save(any(ImportBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rejectsTrailerMismatchBeforeInsertingRows() throws IOException {
        Path file = writeFile("bad.csv", """
                %s
                BANK,INV-1,2025-03-01,100.00,IDR,row
                5
                """.formatted(HEADER));
        ImportBatch batch = batch(1L, file);
        when(batches.findById(1L)).thenReturn(Optional.of(batch));

        service.process(new FileImportRequested(1L, file.toString()));

        assertThat(batch.getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(batch.getErrorMessage())
                .contains("Row count mismatch")
                .contains("declares 5")
                .contains("1 data row");
        assertThat(batch.getTotalRows()).isEqualTo(1);
        assertThat(batch.getDeclaredRows()).isEqualTo(5);
        verifyNoInteractions(jdbc);
    }

    @Test
    void completesValidFileAndMovesItToArchive() throws IOException {
        Path file = writeFile("ok.csv", """
                %s
                BANK,INV-1,2025-03-01,100.00,IDR,row
                INTERNAL,INV-1,2025-03-01,100.00,IDR,row
                2
                """.formatted(HEADER));
        ImportBatch batch = batch(2L, file);
        when(batches.findById(2L)).thenReturn(Optional.of(batch));
        when(jdbc.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
                .thenReturn(new int[]{1, 1});
        when(cacheManager.getCache("transaction-candidates")).thenReturn(null);

        service.process(new FileImportRequested(2L, file.toString()));

        assertThat(batch.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(batch.getTotalRows()).isEqualTo(2);
        assertThat(batch.getDeclaredRows()).isEqualTo(2);
        assertThat(batch.getInsertedRows()).isEqualTo(2);
        assertThat(batch.getDuplicateRows()).isZero();
        assertThat(batch.getRejectedRows()).isZero();
        verify(jdbc).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
        assertThat(Files.exists(file)).isFalse();
        try (Stream<Path> archived = Files.list(tempDir.resolve("archive"))) {
            assertThat(archived).hasSize(1);
        }
    }

    private Path writeFile(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private ImportBatch batch(long id, Path file) {
        ImportBatch batch = new ImportBatch(file.getFileName().toString(), "hash-" + id, file.toString());
        ReflectionTestUtils.setField(batch, "id", id);
        return batch;
    }
}
