# Hướng dẫn Sử dụng Hệ thống (User Manual — OctaPoint Portal)

Tài liệu hướng dẫn thao tác trên giao diện web Next.js (`http://localhost:3000`) cho 3 vai trò: **Merchant Owner**, **Merchant Staff (Read-Only)** và **System Admin**.

---

## 1. Vai trò Doanh nghiệp (Merchant Owner)

### 1.1. Đăng ký & Đăng nhập (`/register`, `/login`)
1. Truy cập `/register`, điền Tên doanh nghiệp, Họ tên, Email, SĐT, Mật khẩu (tối thiểu 8 ký tự).
2. Tài khoản được khởi tạo với Tenant ở trạng thái **`PENDING`** (chưa thể tạo API Key cho đến khi Admin duyệt).
3. Khi trạng thái là **`APPROVED`**, đăng nhập tại `/login` để vào `/merchant`.

### 1.2. Quản lý API Key & Phân quyền Scope (`/merchant/api-keys`)
- **Tạo API Key mới**: Nhập tên key (vd: `POS Quầy 1`, `AI Assistant Read-Only`) và chọn quyền:
  - `ISSUE,REDEEM,READ`: Dành cho backend POS / E-commerce / SDK.
  - `READ`: Dành cho AI Assistant / MCP Server (`FR-17`, chặn mọi lệnh ghi bằng `403 INSUFFICIENT_SCOPE`).
- **Sao chép `API Secret`**: Secret chỉ hiển thị **duy nhất 1 lần** ngay lúc tạo hoặc **Xoay vòng (Rotate)** vì được mã hóa AES-256-GCM trên DB.
- **Thu hồi (Revoke)**: Vô hiệu hóa ngay lập tức các key bị lộ hoặc ngừng sử dụng.

### 1.3. Cấu hình Rule & Hạng thành viên (`/merchant/rule-config` — `FR-08`, `FR-18`)
- **Tích điểm (Earn)**: Số tiền chi tiêu (VND) để quy đổi `1` điểm (vd: `1000` VND = `1` điểm).
- **Hạn sử dụng điểm**: Số ngày hiệu lực của điểm phát hành (`pointExpiryDays`, mặc định `365`).
- **Hạng thành viên & Hệ số nhân**:
  - Ngưỡng `SILVER` (vd: `500` điểm tích lũy) với hệ số nhân `1.20x`.
  - Ngưỡng `GOLD` (vd: `2000` điểm tích lũy) với hệ số nhân `1.50x`.
  - Có khung **Thử nghiệm tính điểm** trực tiếp theo từng hạng ngay trên giao diện trước khi lưu.
- **Quy đổi (Redeem)**: Giá trị VND tương ứng của `1` điểm khi khách dùng điểm thanh toán.

### 1.4. Theo dõi Giao dịch Realtime & Sui Explorer (`/merchant/history` — `FR-07`, `FR-23`)
- Hiển thị trạng thái **`Live SSE Connected`**: Bảng giao dịch tự động làm mới ngay khi Outbox Worker hoàn tất settlement lên Sui Testnet mà không cần F5.
- Bấm vào cột **Sui Tx Digest** (khi `settlementMode = LIVE`) để mở trực tiếp giao dịch trên `https://suiscan.xyz/testnet/tx/<digest>`.
- Dùng ô lọc theo `Customer ID` để xem số dư và toàn bộ biến động của từng khách hàng.

### 1.5. Phân tích & Cảnh báo Gian lận (`/merchant/analytics` — `FR-19`, `FR-22`)
- Thống kê tổng số giao dịch `ISSUE`/`REDEEM`, tỷ lệ đổi điểm (`redemptionRatePercent`), biểu đồ lưu lượng theo ngày và danh sách Top Khách hàng.
- Bảng **Cảnh báo Bất thường (`FR-22`)**: Ghi nhận giao dịch cấp điểm lớn đột biến (`HIGH_SINGLE_ISSUE >= 5.000` điểm) hoặc tần suất cao (`HIGH_FREQUENCY_CUSTOMER >= 15` tx/phút).

### 1.6. Webhook, Nhân viên & Hóa đơn SaaS (`/merchant/settings`)
- **Webhook (`FR-28`)**: Cấu hình URL nhận sự kiện `settlement.settled` kèm `Webhook Secret` và bấm **Gửi Webhook Test** để kiểm tra chữ ký HMAC trên hệ thống merchant.
- **Tài khoản STAFF (`FR-31`)**: Tạo tài khoản nhân viên thuộc doanh nghiệp để xem dashboard, lịch sử và analytics.
- **Gói cước SaaS (`FR-20`)**: Xem lượng gas đã tiêu thụ (`gasUsedMist / gasQuotaMist`), hóa đơn tạm tính kỳ hiện tại (`SIMULATED`) và chuyển đổi giữa các gói `STARTER` / `GROWTH` / `ENTERPRISE`.

---

## 2. Vai trò Nhân viên (Merchant Staff — Read-Only `FR-31`)

- Đăng nhập tại `/login` bằng tài khoản được Owner cấp; thanh bên sẽ gắn nhãn **`STAFF (Read-Only)`**.
- Có quyền xem **Tổng quan** (`/merchant`), **Phân tích & Cảnh báo** (`/merchant/analytics`), **Lịch sử & Số dư** (`/merchant/history`) và nhận sự kiện SSE realtime.
- Không thể truy cập các thao tác thay đổi API Key, Rule, Webhook, nhân viên hoặc gói cước (`HTTP 403 FORBIDDEN_MERCHANT_OWNER_ONLY`).

---

## 3. Vai trò Quản trị viên (System Admin)

### 3.1. Duyệt Tenant & Điều chỉnh Gas Quota (`/admin/tenants` — `FR-02`, `FR-27`)
- Duyệt (`APPROVED`), từ chối (`REJECTED`) hoặc tạm khóa (`SUSPENDED`) doanh nghiệp đăng ký.
- Theo dõi mức tiêu thụ `gasUsedMist / gasQuotaMist` của từng Tenant và cập nhật hạn mức MIST mới tại chỗ (bấm **Set**). Khi nâng quota cho tenant bị `PAUSED_GAS_QUOTA`, backend tự động đưa các event bị tạm dừng về lại `PENDING`.

### 3.2. Dashboard Giám sát, Đối soát & Neo Hash (`/admin`)
- **Thống kê tổng quan & Micrometer Metrics (`NFR-06`)**: Theo dõi số giao dịch `Issue OK`, `Redeem OK`, `HMAC Rejected`, `Settled`, `Settled Fail` và `Anomalies`.
- **Đối soát On-Chain vs DB (`FR-29`)**: Bấm **Chạy Đối soát** để so sánh tổng điểm đã settle trong PostgreSQL với số dư đọc trực tiếp trên Sui Testnet.
- **Neo Hash Ledger (`FR-30`)**: Bấm **Neo Hash ngay** để băm SHA-256 toàn bộ sổ cái `ledger_entries` theo thứ tự bất biến và ghi nhận `suiTxDigest` kiểm chứng lên Sui Testnet.
- **Xử lý Outbox Settlement (`FR-33`)**: Lọc danh sách theo `PENDING` / `SETTLED` / `FAILED` / `PAUSED_GAS_QUOTA` và bấm **Retry** cho các event lỗi.

### 3.3. Nhật ký Kiểm toán Bảo mật (`/admin/audit-logs` — `FR-15`)
- Truy vết mọi hành động quản trị, tạo/thu hồi key, cảnh báo replay (`HMAC_REPLAY_DETECTED`), sai chữ ký và đối soát mà không bao giờ làm lộ `apiSecret`.

---

## 4. Xử lý Sự cố Thường gặp

1. **Merchant tạo API Key bị báo lỗi `403 TENANT_NOT_APPROVED`**: Cần đăng nhập Admin (`/admin/tenants`) và chuyển trạng thái doanh nghiệp sang `APPROVED`.
2. **Outbox chuyển sang `PAUSED_GAS_QUOTA`**: Admin cần tăng `Gas Quota (MIST)` cho tenant tại `/admin/tenants`.
3. **API trả `401 AUTH_NONCE_REUSED` hoặc `AUTH_TIMESTAMP_EXPIRED`**: Kiểm tra đồng bộ NTP trên máy POS và đảm bảo mỗi lần gửi đều sinh `X-OP-Nonce` UUID v4 mới.
