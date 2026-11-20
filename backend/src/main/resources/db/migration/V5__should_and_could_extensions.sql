ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS gas_quota_mist BIGINT NOT NULL DEFAULT 500000000,
    ADD COLUMN IF NOT EXISTS gas_used_mist BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS webhook_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS webhook_secret VARCHAR(255),
    ADD COLUMN IF NOT EXISTS subscription_plan VARCHAR(50) NOT NULL DEFAULT 'STARTER';

ALTER TABLE tenant_rules
    ADD COLUMN IF NOT EXISTS point_expiry_days INT NOT NULL DEFAULT 365,
    ADD COLUMN IF NOT EXISTS silver_threshold_points BIGINT NOT NULL DEFAULT 500,
    ADD COLUMN IF NOT EXISTS gold_threshold_points BIGINT NOT NULL DEFAULT 2000,
    ADD COLUMN IF NOT EXISTS silver_multiplier NUMERIC(5, 2) NOT NULL DEFAULT 1.20,
    ADD COLUMN IF NOT EXISTS gold_multiplier NUMERIC(5, 2) NOT NULL DEFAULT 1.50;

ALTER TABLE customer
    ADD COLUMN IF NOT EXISTS lifetime_earned_points BIGINT NOT NULL DEFAULT 0;

ALTER TABLE ledger_entries
    ADD COLUMN IF NOT EXISTS applied_tier VARCHAR(30) DEFAULT 'BRONZE',
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS gas_used_mist BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS webhook_deliveries (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    event_id VARCHAR(64),
    target_url VARCHAR(500) NOT NULL,
    payload TEXT NOT NULL,
    signature VARCHAR(128) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    last_http_status INT,
    last_error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reconciliation_reports (
    id VARCHAR(64) PRIMARY KEY,
    db_settled_issue_points BIGINT NOT NULL,
    db_settled_redeem_points BIGINT NOT NULL,
    chain_balance_value BIGINT,
    delta_points BIGINT,
    status VARCHAR(30) NOT NULL,
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS anomaly_alerts (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    customer_id VARCHAR(128),
    transaction_id VARCHAR(64),
    rule_code VARCHAR(64) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ledger_anchors (
    id VARCHAR(64) PRIMARY KEY,
    entry_count BIGINT NOT NULL,
    batch_sha256_hash VARCHAR(64) NOT NULL,
    sui_tx_digest VARCHAR(128),
    walrus_blob_id VARCHAR(128),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
