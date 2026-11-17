CREATE INDEX IF NOT EXISTS idx_ledger_tenant_cust_created
    ON ledger_entries (tenant_id, customer_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_outbox_aggregate_id
    ON outbox_events (aggregate_id);

CREATE INDEX IF NOT EXISTS idx_outbox_tenant_cust_status
    ON outbox_events (tenant_id, customer_id, status);
