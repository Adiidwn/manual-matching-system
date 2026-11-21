CREATE TABLE import_batches (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_hash VARCHAR(64) NOT NULL UNIQUE,
    file_path VARCHAR(500) NOT NULL,
    status VARCHAR(24) NOT NULL,
    total_rows INTEGER NOT NULL DEFAULT 0,
    processed_rows INTEGER NOT NULL DEFAULT 0,
    inserted_rows INTEGER NOT NULL DEFAULT 0,
    duplicate_rows INTEGER NOT NULL DEFAULT 0,
    rejected_rows INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    CONSTRAINT ck_import_status CHECK (status IN ('RECEIVED', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

ALTER TABLE transactions ADD COLUMN import_batch_id BIGINT REFERENCES import_batches(id);
ALTER TABLE transactions ADD COLUMN row_number INTEGER;

CREATE INDEX idx_transactions_source_reference ON transactions (source, external_reference);
CREATE INDEX idx_transactions_batch ON transactions (import_batch_id);
CREATE INDEX idx_import_batches_created ON import_batches (created_at, id);
