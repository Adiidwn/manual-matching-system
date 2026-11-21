CREATE TABLE matches (
    id BIGSERIAL PRIMARY KEY,
    bank_transaction_id BIGINT NOT NULL REFERENCES transactions(id),
    internal_transaction_id BIGINT NOT NULL REFERENCES transactions(id),
    status VARCHAR(24) NOT NULL,
    maker VARCHAR(100) NOT NULL,
    checker VARCHAR(100),
    signer VARCHAR(100),
    rejection_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_match_status CHECK (status IN ('SUBMITTED', 'CHECKED', 'SIGNED', 'REJECTED'))
);
CREATE UNIQUE INDEX uq_active_bank_match ON matches(bank_transaction_id) WHERE status <> 'REJECTED';
CREATE UNIQUE INDEX uq_active_internal_match ON matches(internal_transaction_id) WHERE status <> 'REJECTED';
CREATE INDEX idx_matches_work_queue ON matches(status, created_at, id);

CREATE TABLE audit_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL REFERENCES matches(id),
    actor VARCHAR(100) NOT NULL,
    role VARCHAR(24) NOT NULL,
    action VARCHAR(40) NOT NULL,
    note VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_match_time ON audit_events(match_id, occurred_at, id);
