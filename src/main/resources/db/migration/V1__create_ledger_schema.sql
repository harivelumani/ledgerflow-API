CREATE TABLE accounts (
    id CHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    balance DECIMAL(19, 2) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT ck_accounts_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_accounts_balance_non_negative CHECK (balance >= 0)
);

CREATE TABLE transfers (
    id CHAR(36) NOT NULL,
    source_account_id CHAR(36) NOT NULL,
    destination_account_id CHAR(36) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    reference_id VARCHAR(64) NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_transfers PRIMARY KEY (id),
    CONSTRAINT uk_transfers_reference UNIQUE (reference_id),
    CONSTRAINT ck_transfers_distinct_accounts CHECK (source_account_id <> destination_account_id),
    CONSTRAINT ck_transfers_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transfers_reference_not_blank CHECK (CHAR_LENGTH(TRIM(reference_id)) > 0),
    CONSTRAINT ck_transfers_description_not_blank CHECK (CHAR_LENGTH(TRIM(description)) > 0),
    CONSTRAINT fk_transfers_source FOREIGN KEY (source_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_transfers_destination FOREIGN KEY (destination_account_id) REFERENCES accounts (id)
);

CREATE TABLE ledger_transactions (
    id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    type VARCHAR(16) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    description VARCHAR(255) NOT NULL,
    reference_id VARCHAR(64) NOT NULL,
    transfer_id CHAR(36) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_ledger_transactions PRIMARY KEY (id),
    CONSTRAINT uk_txn_account_reference UNIQUE (account_id, reference_id),
    CONSTRAINT ck_txn_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_txn_type CHECK (type IN ('CREDIT', 'DEBIT')),
    CONSTRAINT ck_txn_description_not_blank CHECK (CHAR_LENGTH(TRIM(description)) > 0),
    CONSTRAINT ck_txn_reference_not_blank CHECK (CHAR_LENGTH(TRIM(reference_id)) > 0),
    CONSTRAINT fk_txn_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_txn_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (id)
);

CREATE INDEX idx_txn_account_created ON ledger_transactions (account_id, created_at, id);
CREATE INDEX idx_txn_account_type_created ON ledger_transactions (account_id, type, created_at, id);
CREATE INDEX idx_txn_transfer ON ledger_transactions (transfer_id);
CREATE INDEX idx_transfers_source ON transfers (source_account_id);
CREATE INDEX idx_transfers_destination ON transfers (destination_account_id);
