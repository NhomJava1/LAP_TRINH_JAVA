# ADR-0001: Tách ChainClient khỏi Signer

- **Trạng thái:** Accepted
- **Ngày:** 2026-11-05

## Bối cảnh

Backend cần một ranh giới ổn định để gọi các thao tác nghiệp vụ trên blockchain: tạo account, mint, redeem và đọc account. Signer hiện là dịch vụ TypeScript chịu trách nhiệm gửi giao dịch lên Sui; việc quản lý quyền ký và ký giao dịch là trách nhiệm nhạy cảm, khác với hợp đồng nghiệp vụ mà backend cần sử dụng.

Gộp hai trách nhiệm khiến phần nghiệp vụ phụ thuộc trực tiếp vào cách ký, cấu hình khóa hoặc API của một signer cụ thể. Đồng thời, sẽ khó thay backend chain hay dùng implementation giả trong kiểm thử.

## Quyết định

Định nghĩa interface Java `ChainClient` trong package `com.octapoint.core.chain` làm abstraction cho các thao tác nghiệp vụ chain. Các lời gọi sử dụng DTO kiểu rõ ràng và trả về dữ liệu account, transaction hoặc `Optional` khi đọc account không tìm thấy.

`ChainClient` và `Signer` là hai trách nhiệm riêng:

- `ChainClient` nhận yêu cầu nghiệp vụ chain và cung cấp kết quả chain cho backend.
- `Signer` giữ trách nhiệm ký và phát giao dịch, cùng với quyền truy cập khóa cần thiết. Một implementation của `ChainClient` có thể giao tiếp với Signer nhưng không làm lộ khóa hay chuyển trách nhiệm ký sang application/domain layer.

`MockChainClient` là implementation khung của interface. Khi chưa có mô phỏng giao dịch được định nghĩa, các phương thức ném `UnsupportedOperationException` để tránh trả kết quả thành công giả. Các implementation của `ChainClient` được giới hạn trong package `chain` bằng kiểm thử ArchUnit.

## Hệ quả

- Application code có thể phụ thuộc vào interface thay vì implementation cụ thể; mock/test client và implementation thật có thể thay thế nhau qua cùng contract.
- Việc đổi giao thức Signer hoặc chain không buộc thay đổi contract nghiệp vụ, miễn là adapter giữ đúng interface.
- Kết nối giữa `ChainClient` và Signer, xử lý retry/idempotency và mô phỏng dữ liệu chưa nằm trong quyết định này; cần được triển khai theo các ticket tương ứng.
- Move contract hiện tại chưa cung cấp đủ thao tác tạo account và redeem. Contract Java không khẳng định các thao tác đó đã có implementation on-chain.
