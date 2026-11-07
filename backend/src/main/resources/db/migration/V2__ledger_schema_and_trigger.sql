-- Bảng customer quản lý số dư và chuỗi sequence theo customer
CREATE TABLE customer (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    balance NUMERIC(38, 0) NOT NULL DEFAULT 0,
    chain_seq BIGINT NOT NULL DEFAULT -1, -- Bắt đầu từ -1 để lần issue đầu tiên tăng lên thành 0
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Bảng lưu trữ Idempotency Record
CREATE TABLE idempotency_record (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    response_body TEXT NOT NULL,
    status_code INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Database Trigger chặn hoàn toàn UPDATE và DELETE trên bảng ledger_entries
CREATE OR REPLACE FUNCTION prevent_ledger_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'UPDATE and DELETE operations on ledger_entries are strictly prohibited.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_ledger_mod
BEFORE UPDATE OR DELETE ON ledger_entries
FOR EACH ROW
EXECUTE FUNCTION prevent_ledger_modification();