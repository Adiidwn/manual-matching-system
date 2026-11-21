package com.example.reconciliation.imports;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "import_batches")
public class ImportBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;
    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private ImportStatus status;
    @Column(name = "total_rows", nullable = false)
    private int totalRows;
    @Column(name = "declared_rows")
    private Integer declaredRows;
    @Column(name = "processed_rows", nullable = false)
    private int processedRows;
    @Column(name = "inserted_rows", nullable = false)
    private int insertedRows;
    @Column(name = "duplicate_rows", nullable = false)
    private int duplicateRows;
    @Column(name = "rejected_rows", nullable = false)
    private int rejectedRows;
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    protected ImportBatch() {}

    public ImportBatch(String fileName, String fileHash, String filePath) {
        this.fileName = truncate(fileName);
        this.fileHash = fileHash;
        this.filePath = filePath;
        this.status = ImportStatus.RECEIVED;
        this.createdAt = OffsetDateTime.now();
    }

    public void reset(String fileName, String filePath) {
        this.fileName = truncate(fileName);
        this.filePath = filePath;
        this.status = ImportStatus.RECEIVED;
        this.totalRows = 0;
        this.declaredRows = null;
        this.processedRows = 0;
        this.insertedRows = 0;
        this.duplicateRows = 0;
        this.rejectedRows = 0;
        this.errorMessage = null;
        this.finishedAt = null;
    }

    public void begin(String filePath) {
        reset(fileName, filePath);
        this.status = ImportStatus.PROCESSING;
    }

    public void progress(int processedRows, int insertedRows, int duplicateRows, int rejectedRows) {
        this.processedRows = processedRows;
        this.insertedRows = insertedRows;
        this.duplicateRows = duplicateRows;
        this.rejectedRows = rejectedRows;
    }

    public void complete(int processedRows, int insertedRows, int duplicateRows, int rejectedRows, OffsetDateTime finishedAt) {
        progress(processedRows, insertedRows, duplicateRows, rejectedRows);
        this.status = ImportStatus.COMPLETED;
        this.finishedAt = finishedAt;
        this.errorMessage = null;
    }

    public void fail(String reason) {
        if (this.status == ImportStatus.COMPLETED) return;
        this.status = ImportStatus.FAILED;
        this.finishedAt = OffsetDateTime.now();
        this.errorMessage = reason == null || reason.length() <= 1000 ? reason : reason.substring(0, 1000);
    }

    public Long getId() { return id; }
    public String getFileName() { return fileName; }
    public String getFileHash() { return fileHash; }
    public String getFilePath() { return filePath; }
    public ImportStatus getStatus() { return status; }
    public int getTotalRows() { return totalRows; }
    public Integer getDeclaredRows() { return declaredRows; }
    public int getProcessedRows() { return processedRows; }
    public int getInsertedRows() { return insertedRows; }
    public int getDuplicateRows() { return duplicateRows; }
    public int getRejectedRows() { return rejectedRows; }
    public String getErrorMessage() { return errorMessage; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
    public void setDeclaredRows(Integer declaredRows) { this.declaredRows = declaredRows; }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 255 ? value : value.substring(value.length() - 255);
    }
}
