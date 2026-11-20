# ADR-0003: HMAC-SHA256 cho xác thực API Merchant

- **Trạng thái:** Accepted
- **Ngày:** 2026-11-06

## Bối cảnh

Merchant System gọi API server-to-server và không dựa vào session người dùng. Mỗi request cần được xác thực, bảo vệ tính toàn vẹn và chống phát lại. JWT phù hợp với phiên người dùng/Portal nhưng không thay thế chữ ký theo từng request cho API key của merchant.

## Quyết định

Mỗi request Merchant API mang các header:

| Header | Ý nghĩa |
|---|---|
| `X-OP-Key` | ID của API key |
| `X-OP-Timestamp` | Unix epoch seconds |
| `X-OP-Nonce` | Nonce duy nhất, khuyến nghị UUID |
| `X-OP-Signature` | Chữ ký HMAC-SHA256 |

Chuỗi ký được xây dựng theo thứ tự:

```text
HTTP method
request path
canonical sorted query string
timestamp
nonce
SHA256(request body)
```

Các thành phần được phân tách bằng newline; query được chuẩn hóa và sắp xếp ổn định trước khi ký. SDK và server phải dùng cùng quy tắc canonicalization, encoding, chữ thường/hoa của method và cách biểu diễn body.

- Chấp nhận timestamp lệch tối đa ±300 giây.
- Sau khi chữ ký hợp lệ, lưu nonce bằng Redis `SETNX` với TTL 600 giây; nonce đã tồn tại bị từ chối. Redis không khả dụng thì từ chối request cần xác thực, không bỏ qua kiểm tra replay.
- Lưu API key secret đã mã hóa AES-256-GCM; master key lấy từ biến môi trường/secret manager, không lưu trong repository.
- Giới hạn tốc độ hai tầng: theo IP (Bucket4j in-memory) và theo API key (Redis).
- Idempotency key giải quyết gửi lại nghiệp vụ; nonce giải quyết replay một request đã ký. Hai cơ chế có mục đích và vòng đời khác nhau.
- JWT/RBAC tiếp tục được dùng cho Portal; không dùng HMAC thay thế xác thực người dùng.

## Hệ quả

- Request được xác thực và bảo vệ toàn vẹn; timestamp/nonce giới hạn khả năng phát lại.
- Merchant TypeScript SDK phải implement đúng canonical string và cần test vectors dùng chung với backend.
- Redis trở thành phụ thuộc cho nonce và rate limit theo API key. Khi Redis lỗi, hệ thống từ chối request thay vì vô hiệu hóa chống replay.
- Khóa mã hóa cần được quản lý, luân chuyển và backup an toàn; AES-GCM cần nonce IV ngẫu nhiên duy nhất cho mỗi mã hóa.

## Ghi chú tương thích

Đặc tả này là hợp đồng mục tiêu nhưng hiện chưa đồng bộ với [`HMAC_SPEC_V1.md`](../HMAC_SPEC_V1.md) và [`HmacFilter`](../../backend/src/main/java/com/octapoint/core/security/HmacFilter.java): tên header, nội dung canonical string, định dạng chữ ký và thứ tự kiểm tra khác nhau. Mã hiện tại còn dùng secret thử nghiệm. Không phát hành tích hợp Merchant mới cho đến khi các tài liệu, backend và SDK được hợp nhất cùng test vectors.
