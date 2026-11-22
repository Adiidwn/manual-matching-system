CREATE TABLE import_header_mappings (
    id BIGSERIAL PRIMARY KEY,
    canonical_column VARCHAR(64) NOT NULL,
    header_name VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_import_header_mapping_column CHECK (canonical_column IN (
        'source', 'external_reference', 'transaction_date', 'amount', 'currency', 'description'))
);

CREATE UNIQUE INDEX uq_import_header_mapping_name ON import_header_mappings (lower(header_name));
