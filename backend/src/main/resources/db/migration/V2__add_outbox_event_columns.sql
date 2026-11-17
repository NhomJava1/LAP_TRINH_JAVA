-- Bổ sung các cột phục vụ định tuyến thứ tự và tracking thời gian xử lý cho Outbox Worker

ALTER TABLE outbox_event 
    ADD COLUMN IF NOT EXISTS chain_seq BIGINT,
    ADD COLUMN IF NOT EXISTS order_key BIGINT,
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMP WITH TIME ZONE;

-- Backfill dữ liệu cho các bản ghi cũ (nếu có):
-- CREATE_ACCOUNT có order_key = -1 để ưu tiên chạy trước, các sự kiện khác lấy theo chain_seq
UPDATE outbox_event 
SET order_key = CASE 
    WHEN event_type = 'CREATE_ACCOUNT' THEN -1 
    ELSE COALESCE(chain_seq, 0) 
END
WHERE order_key IS NULL;

-- Tạo index hỗ trợ worker truy vấn và polling sự kiện hiệu quả theo trạng thái và thứ tự
CREATE INDEX IF NOT EXISTS idx_outbox_event_worker_poll 
ON outbox_event (status, order_key, created_at);