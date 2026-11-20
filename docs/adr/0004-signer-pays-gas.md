# ADR-0004: Signer tự trả Gas (không dùng Sponsored Transaction)

- **Trạng thái:** Accepted
- **Ngày:** 2026-11-06

## Bối cảnh

Sui hỗ trợ sponsored transaction để tách người ký và người trả gas. Tuy nhiên, luồng sponsored transaction cần phối hợp hai chữ ký và phụ thuộc dịch vụ Enoki bên ngoài. MVP cần giảm số thành phần vận hành và giữ luồng settlement có thể kiểm soát.

## Quyết định

- Signer service vừa ký giao dịch vừa trả gas từ ví platform.
- MVP dùng một ví platform do Signer quản lý; khóa riêng chỉ được nạp tại runtime của Signer.
- Không dùng zkLogin hoặc Enoki/sponsored transaction trong MVP.
- Worker xử lý tuần tự giao dịch của cùng một customer để tránh tranh chấp tài nguyên và bảo toàn thứ tự `chain_seq`. Có thể xử lý song song giữa các customer nếu giới hạn tài nguyên/gas cho phép.

## Hệ quả

- Luồng settlement đơn giản hơn và không cần phối hợp sponsor bên ngoài.
- Platform chịu chi phí gas; cần theo dõi gas balance, cảnh báo ngưỡng thấp và quy trình nạp ví Testnet.
- Hạn mức gas theo tenant có thể được bổ sung sau (FR-27, Should).
- MVP không có user-facing wallet; khách hàng không cần ký hoặc quản lý gas.
- Một ví duy nhất làm tăng phạm vi ảnh hưởng nếu khóa bị lộ hoặc ví cạn gas; cần bảo vệ secret, giới hạn truy cập Signer và giám sát giao dịch.
- Worker chưa được triển khai và Move contract hiện tại chưa có account/sequence theo customer; thứ tự xử lý và mô hình ví này là kiến trúc mục tiêu, không phải bảo đảm runtime đang hoạt động.
