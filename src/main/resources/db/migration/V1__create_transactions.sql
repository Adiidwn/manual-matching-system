CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(24) NOT NULL,
    external_reference VARCHAR(100) NOT NULL,
    transaction_date DATE NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(24) NOT NULL DEFAULT 'UNMATCHED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_transaction_source CHECK (source IN ('BANK', 'INTERNAL')),
    CONSTRAINT ck_transaction_status CHECK (status IN ('UNMATCHED', 'PENDING_APPROVAL', 'RECONCILED'))
);

CREATE INDEX idx_transactions_queue ON transactions (status, transaction_date, id);
CREATE INDEX idx_transactions_candidate ON transactions (source, status, amount, transaction_date);
CREATE INDEX idx_transactions_reference ON transactions (external_reference);
