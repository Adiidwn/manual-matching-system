package com.example.reconciliation.imports;

import java.time.OffsetDateTime;

public record ImportView(Long id, String fileName, ImportStatus status,
                         int totalRows, Integer declaredRows, int processedRows, int insertedRows,
                         int duplicateRows, int rejectedRows, String errorMessage,
                         OffsetDateTime createdAt, OffsetDateTime finishedAt) {
    static ImportView from(ImportBatch batch) {
        return new ImportView(batch.getId(), batch.getFileName(), batch.getStatus(),
                batch.getTotalRows(), batch.getDeclaredRows(), batch.getProcessedRows(), batch.getInsertedRows(),
                batch.getDuplicateRows(), batch.getRejectedRows(), batch.getErrorMessage(),
                batch.getCreatedAt(), batch.getFinishedAt());
    }
}
