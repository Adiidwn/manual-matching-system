package com.example.reconciliation.imports;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ImportBatchTest {
    @Test
    void startsAsReceived() {
        ImportBatch batch = new ImportBatch("transactions.csv", "hash", "/tmp/transactions.csv");

        assertThat(batch.getStatus()).isEqualTo(ImportStatus.RECEIVED);
        assertThat(batch.getFileName()).isEqualTo("transactions.csv");
        assertThat(batch.getFileHash()).isEqualTo("hash");
        assertThat(batch.getTotalRows()).isZero();
        assertThat(batch.getDeclaredRows()).isNull();
        assertThat(batch.getCreatedAt()).isNotNull();
    }

    @Test
    void completesLifecycle() {
        ImportBatch batch = new ImportBatch("transactions.csv", "hash", "/tmp/transactions.csv");
        batch.begin("/tmp/processing/transactions.csv");
        batch.setTotalRows(10);
        batch.progress(5, 4, 1, 0);
        batch.complete(10, 8, 2, 0, OffsetDateTime.now());

        assertThat(batch.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(batch.getTotalRows()).isEqualTo(10);
        assertThat(batch.getProcessedRows()).isEqualTo(10);
        assertThat(batch.getInsertedRows()).isEqualTo(8);
        assertThat(batch.getDuplicateRows()).isEqualTo(2);
        assertThat(batch.getRejectedRows()).isZero();
        assertThat(batch.getFinishedAt()).isNotNull();
    }

    @Test
    void failDoesNotOverrideCompletedBatch() {
        ImportBatch batch = new ImportBatch("transactions.csv", "hash", "/tmp/transactions.csv");
        batch.complete(1, 1, 0, 0, OffsetDateTime.now());
        batch.fail("late failure");

        assertThat(batch.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(batch.getErrorMessage()).isNull();
    }

    @Test
    void failMarksBatchFailed() {
        ImportBatch batch = new ImportBatch("transactions.csv", "hash", "/tmp/transactions.csv");
        batch.fail("broken file");

        assertThat(batch.getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(batch.getErrorMessage()).isEqualTo("broken file");
        assertThat(batch.getFinishedAt()).isNotNull();
    }
}
