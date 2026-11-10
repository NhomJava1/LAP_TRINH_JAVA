# ADR-0002: Outbox Pattern cho Settlement lên Sui

- **Trạng thái:** Accepted
- **Ngày:** 2026-11-06

## Bối cảnh

Giao dịch issue/redeem cần phản hồi nhanh (mục tiêu p95 < 100 ms), trong khi settlement lên blockchain có độ trễ cao và phụ thuộc RPC/validator bên ngoài. Mọi giao dịch nghiệp vụ đã commit phải được settlement; không được mất event khi tiến trình dừng giữa lúc cập nhật database và gửi giao dịch.

Gọi Sui trực tiếp trong request flow làm tăng latency và khiến lỗi blockchain ảnh hưởng tới khả năng ghi nhận nghiệp vụ. PostgreSQL là nguồn dữ liệu có thẩm quyền, vì vậy kết quả nghiệp vụ cần được commit độc lập với trạng thái settlement.

## Quyết định

Dùng Transactional Outbox:

1. Trong cùng một PostgreSQL transaction, ghi cập nhật balance, `ledger_entry`, `idempotency_record` và `outbox_event`.
2. Settlement Worker định kỳ poll các event đủ điều kiện, dự kiến mỗi 5–10 giây. Worker claim batch bằng `SELECT ... FOR UPDATE SKIP LOCKED`, theo thứ tự `chain_seq` cho từng cặp tenant/customer.
3. Worker đánh dấu các event được claim là `PROCESSING` và commit trước khi gọi mạng ngoài; không giữ khóa hoặc transaction DB trong lúc chờ Signer/Sui.
4. Worker gửi batch qua Signer nội bộ, xác minh trạng thái giao dịch và digest rồi mới ghi `chain_tx` và đánh dấu event `SETTLED`.
5. Vòng đời event: `PENDING → PROCESSING → SETTLED`, hoặc `PROCESSING → PENDING` khi cần retry, hoặc `PROCESSING → FAILED` sau khi hết retry/không thể tiếp tục.
6. Retry dùng exponential backoff, tối đa 5 lần. Trước khi gửi lại một giao dịch có kết quả không rõ do timeout, Worker phải kiểm tra trạng thái on-chain khi có thể.
7. Event `FAILED` chặn các event sau của cùng tenant/customer (head-of-line blocking có chủ đích). Customer khác vẫn được xử lý. Admin cần xem và xử lý thủ công theo FR-33 (Could).

## Hệ quả

### Tích cực

- Request nghiệp vụ không bị block bởi latency hoặc sự cố blockchain.
- Balance, ledger và sự kiện cần settlement không thể commit lệch nhau trong database.
- Hệ thống chấp nhận eventual consistency giữa PostgreSQL và Sui; API có thể trả kết quả nghiệp vụ khi settlement còn `PENDING`.

### Đánh đổi và trách nhiệm vận hành

- Worker cần phục hồi các event bị kẹt ở `PROCESSING`, quan sát queue depth, tuổi event, retry và số event `FAILED`.
- Timeout sau khi gửi giao dịch tạo kết quả không xác định; Worker phải tra cứu chain trước retry để tránh gửi trùng.
- Event `FAILED` chặn tiến độ của cùng customer cho tới khi được xử lý. Thao tác retry thủ công thuộc phạm vi FR-33 (Could).
- Outbox không tự bảo đảm exactly-once execution trên blockchain; idempotency/sequence của Move contract và đối soát digest là các biện pháp bổ trợ.
- Worker, cơ chế lease phục hồi và tích hợp outbox-to-Signer chưa có trong runtime hiện tại; cần triển khai và kiểm thử trước khi xem quyết định này là vận hành thực tế.
