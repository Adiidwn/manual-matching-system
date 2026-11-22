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
class ImportProcessorTest {
    private static final String HEADER = "source,external_reference,transaction_date,amount,currency,description";

    @Mock
    private ImportRepository batches;
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private ImportHeaderAliasService headerAliases;

    @TempDir
    Path tempDir;

    private ImportProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ImportProcessor(
                batches, jdbc, cacheManager, headerAliases,
                List.of(new CsvFileParser()),
                tempDir.resolve("archive").toString(),
                tempDir.resolve("error").toString(),
                1000);
        lenient().when(batches.save(any(ImportBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(headerAliases.aliases()).thenReturn(java.util.Map.of());
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

        processor.process(new FileImportRequested(1L, file.toString()));

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

        processor.process(new FileImportRequested(2L, file.toString()));

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

    @Test
    void importsFileWhoseColumnsAreNotInTheCanonicalOrder() throws IOException {
        Path file = writeFile("reordered.csv", """
                description,amount,currency,source,transaction_date,external_reference
                row,100.00,IDR,BANK,2025-03-01,INV-9
                """);
        ImportBatch batch = batch(3L, file);
        when(batches.findById(3L)).thenReturn(Optional.of(batch));
        when(jdbc.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
                .thenReturn(new int[]{1});
        when(cacheManager.getCache("transaction-candidates")).thenReturn(null);

        processor.process(new FileImportRequested(3L, file.toString()));

        assertThat(batch.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(batch.getTotalRows()).isEqualTo(1);
        assertThat(batch.getInsertedRows()).isEqualTo(1);
        verify(jdbc).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    @Test
    void importsFileUsingHeaderAliasesLoadedFromTheDatabase() throws IOException {
        when(headerAliases.aliases()).thenReturn(java.util.Map.of(
                "sumber", "source",
                "no referensi", "external_reference",
                "tanggal", "transaction_date",
                "nominal", "amount",
                "mata uang", "currency",
                "keterangan", "description"));
        Path file = writeFile("aliases.csv", """
                Sumber,No Referensi,Tanggal,Nominal,Mata Uang,Keterangan
                BANK,INV-10,2025-03-01,100.00,IDR,row
                """);
        ImportBatch batch = batch(4L, file);
        when(batches.findById(4L)).thenReturn(Optional.of(batch));
        when(jdbc.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
                .thenReturn(new int[]{1});
        when(cacheManager.getCache("transaction-candidates")).thenReturn(null);

        processor.process(new FileImportRequested(4L, file.toString()));

        assertThat(batch.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(batch.getTotalRows()).isEqualTo(1);
        assertThat(batch.getInsertedRows()).isEqualTo(1);
        assertThat(batch.getRejectedRows()).isZero();
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
