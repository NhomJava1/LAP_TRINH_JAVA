# Hướng dẫn Vận hành và Kịch bản Demo - OctaPoint

Tài liệu này cung cấp hướng dẫn vận hành hệ thống OctaPoint và kịch bản demo chi tiết (10 phút) cho buổi bảo vệ dự án.

## 1. Môi trường vận hành
- **Backend (Spring Boot):** Chạy tại `http://localhost:8080`
- **Frontend (Next.js):** Chạy tại `http://localhost:3000`
- **Database (PostgreSQL & Redis):** Chạy qua Docker Compose.

## 2. Phân công vai trò (5 Thành viên)
- **Thành viên 1 (Admin):** Thao tác trên Admin Portal (Duyệt tenant, xem dashboard, audit log).
- **Thành viên 2 (Merchant):** Thao tác trên Merchant Portal (Đăng ký, tạo API key, xem lịch sử).
- **Thành viên 3 (Developer/Backend):** Chạy SDK, thực hiện các API Call (Issue, Redeem).
- **Thành viên 4 (Developer/Tester):** Kiểm tra bảo mật (HMAC, Idempotency) và giả lập lỗi.
- **Thành viên 5 (Thuyết trình/Điều phối):** Mở Sui Explorer, trình bày tài liệu và kiểm soát thời gian demo.

## 3. Kịch bản Demo (10 phút)
*(Dựa trên mục 4 trong file Requirement Index)*

| Bước | Thời gian | Người thực hiện | Thao tác | Kết quả mong đợi | Yêu cầu |
|---|---|---|---|---|---|
| 1 | 0:00 - 1:00 | Thành viên 2 (Merchant) | Đăng ký tenant "Siêu thị A" trên Portal | Tenant ở trạng thái PENDING. | FR-01 |
| 2 | 1:00 - 2:00 | Thành viên 1 (Admin) | Đăng nhập Admin, duyệt tenant "Siêu thị A" | Tenant chuyển sang APPROVED. | FR-02, FR-04 |
| 3 | 2:00 - 3:00 | Thành viên 2 (Merchant) | Tạo API key và cấu hình rule (1.000 VNĐ = 1 điểm) | Secret chỉ hiển thị một lần. Rule được lưu thành công. | FR-03, FR-08 |
| 4 | 3:00 - 4:30 | Thành viên 3 (Developer) | Dùng Postman/SDK gọi API Issue cho khách C1 (100.000 VNĐ) | API trả về kết quả thành công, cấp 100 điểm. | FR-05, FR-11 |
| 5 | 4:30 - 6:00 | Thành viên 4 (Tester) | 1. Gửi lại request với cùng Idempotency Key.<br>2. Gửi request có chữ ký HMAC sai. | 1. Trả về kết quả cũ, số dư không tăng.<br>2. Nhận lỗi HTTP 401. | FR-05, FR-14 |
| 6 | 6:00 - 7:00 | Thành viên 3 (Developer) | Gọi API Redeem 30 điểm, sau đó thử Redeem 100 điểm khi số dư không đủ | Redeem 30 điểm thành công (còn 70). Redeem 100 điểm bị từ chối (lỗi 400). | FR-06 |
| 7 | 7:00 - 8:00 | Thành viên 2 (Merchant) | Mở lại trang Lịch sử và Số dư trên Portal | Hiển thị đúng số dư (70), lịch sử giao dịch và settlement status. | FR-07, FR-12 |
| 8 | 8:00 - 9:00 | Thành viên 5 (Thuyết trình) | Mở transaction digest của batch đã settle trên Sui Explorer | Giao dịch thực tế được tìm thấy trên Sui Testnet. | FR-09, FR-10, NFR-05 |
| 9 | 9:00 - 10:00 | Thành viên 1 (Admin) | Mở Dashboard và Audit log trên Admin Portal | Thấy số tenant, số giao dịch, trạng thái settlement và log duyệt tenant. | FR-13, FR-15 |

## 4. Phương án dự phòng khi demo
- Nếu Sui Testnet chậm hoặc lỗi, hệ thống được phép chạy với `MockChainClient`.
- Chế độ mock phải hiển thị rõ trạng thái MOCK hoặc SIMULATED.
- Không được gán transaction digest cũ cho một giao dịch mock mới.
- Nếu mạng không ổn định, nhóm sử dụng video demo đã quay trước hoặc báo cáo kiểm thử kèm bằng chứng Explorer.

## 5. Lưu ý vận hành
- Mỗi thành viên phụ trách một phần demo. Nhóm phải diễn tập toàn bộ kịch bản ít nhất hai lần.
- Kiểm tra kỹ file `.env` trước khi chạy demo để đảm bảo kết nối database và API key chính xác.
- Đảm bảo Docker Desktop đã bật và các container (PostgreSQL, Redis) đang chạy trước khi khởi động Backend/Frontend.