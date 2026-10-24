-- 1. Bảng Tenant (Quản lý khách hàng/tổ chức sử dụng dịch vụ)
CREATE TABLE IF NOT EXISTS tenants (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Bảng ApiKey (Quản lý khóa API của từng Tenant)
CREATE TABLE IF NOT EXISTS api_keys (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    key_hash VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_api_key_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

-- 3. Bảng Balance (Quản lý số dư tín dụng/point của tenant)
CREATE TABLE IF NOT EXISTS balances (
    tenant_id VARCHAR(64) PRIMARY KEY,
    amount NUMERIC(38, 2) NOT NULL DEFAULT 0.00,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_balance_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

-- 4. Bảng LedgerEntry (Sổ cái ghi nhận mọi biến động giao dịch credit)
CREATE TABLE IF NOT EXISTS ledger_entries (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    type VARCHAR(50) NOT NULL,
    amount NUMERIC(38, 2) NOT NULL,
    reference_id VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ledger_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

-- 5. Bảng Outbox (Pattern đảm bảo đồng bộ dữ liệu ra blockchain/event broker)
CREATE TABLE IF NOT EXISTS outbox_events (
    id VARCHAR(64) PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);