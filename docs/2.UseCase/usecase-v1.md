# OctaPoint: Danh sách Actor và Use case v1

| **Phiên bản** | 1.0 (dự thảo) |
|---|---|
| **Ticket** | SCRUM-12 |
| **Căn cứ** | `requirement-index-v0.2.md` (mã FR, NFR, MoSCoW) |
| **Sơ đồ** | `usecase-mvp.png` (Must), `usecase-extended.png` (Should và Could). Mã nguồn: `usecase-mvp.puml`, `usecase-extended.puml` |

---

## 1. Khái niệm dùng trong tài liệu

| Khái niệm | Ý nghĩa | Ví dụ trong OctaPoint |
|---|---|---|
| Actor | Người hoặc hệ thống **bên ngoài** tương tác trực tiếp với hệ thống | Merchant, Sui Blockchain |
| Use case | Một mục tiêu có giá trị mà actor muốn đạt được | Issue điểm |
| Primary actor | Actor chủ động bắt đầu use case | Merchant System gọi issue |
| Secondary actor | Hệ thống ngoài mà use case cần dùng | Sui Blockchain nhận settlement |
| `<<include>>` | Use case A **luôn** dùng use case B | Issue điểm luôn xác thực request |
| `<<extend>>` | Use case B chỉ chạy thêm khi **có điều kiện** | Trả kết quả cũ, chỉ khi Idempotency-Key đã dùng |

---

## 2. Actor

| Actor | Loại | Mô tả | Giao diện | Mức |
|---|---|---|---|---|
| Merchant | Primary | Chủ merchant. Đăng ký tenant, quản lý API key, cấu hình rule, xem số dư và lịch sử | Merchant Portal | Must |
| System Admin | Primary | Vận hành nền tảng. Duyệt tenant, giám sát, xem audit log | Admin Portal | Must |
| Merchant System | Primary | Hệ thống POS hoặc e-commerce của merchant, gọi API qua SDK. Developer là người tích hợp, không phải actor riêng | REST API + HMAC, SDK | Must |
| Sui Blockchain | Secondary | Hệ thống ngoài nhận settlement (Testnet) | Qua service Signer | Must |
| AI Agent | Primary | Trợ lý AI hoặc bot hỗ trợ khách hàng, truy vấn dữ liệu qua MCP | MCP Server | Should |

**Không phải actor, và lý do:**

| Thành phần | Lý do |
|---|---|
| End-user (khách tích điểm) | Không có giao diện trực tiếp trong MVP (không ví, không app). Được đại diện bằng `customerId` do Merchant System gửi. Nếu sau này cho khách tự xem điểm thì mới thêm thành actor |
| Worker outbox, Signer, Scheduler | Là thành phần bên trong hệ thống |
| Developer | Là người dùng SDK, hành vi của họ thể hiện qua Merchant System |

---

## 3. Use case

### 3.1. Must (MVP)

| ID | Use case | Actor | Mô tả ngắn | FR |
|---|---|---|---|---|
| UC-01 | Đăng ký tenant | Merchant | Tự đăng ký, tenant ở trạng thái PENDING | FR-01 |
| UC-02 | Đăng nhập Portal | Merchant, Admin | Đăng nhập, nhận JWT theo vai trò | FR-04 |
| UC-03 | Xem trạng thái tenant | Merchant | Xem tenant đang PENDING, APPROVED, REJECTED hay SUSPENDED | FR-12 |
| UC-04 | Duyệt, từ chối, khóa tenant | Admin | Đổi trạng thái tenant, ghi audit log | FR-02 |
| UC-05 | Tạo API key | Merchant | Sinh key và secret, secret chỉ hiện một lần | FR-03 |
| UC-06 | Xoay vòng API key | Merchant | Tạo key mới và thu hồi key cũ | FR-03 |
| UC-07 | Thu hồi API key | Merchant | Key bị thu hồi không còn xác thực được | FR-03 |
| UC-08 | Cấu hình rule earn/redeem | Merchant | Đặt `earnVndPerPoint` và `redeemValueVndPerPoint` | FR-08, FR-12 |
| UC-09 | Issue điểm | Merchant System | Cộng điểm theo giá trị đơn hàng và rule | FR-05, FR-11 |
| UC-10 | Redeem điểm | Merchant System | Trừ điểm khi đủ số dư, không để số dư âm | FR-06, FR-11 |
| UC-11 | Tra cứu số dư | Merchant System, Merchant | Xem số dư hiện tại của một khách hàng | FR-07, FR-11, FR-12 |
| UC-12 | Tra cứu lịch sử giao dịch | Merchant System, Merchant | Xem giao dịch kèm settlement status và digest | FR-07, FR-11, FR-12 |
| UC-13 | Xác thực và giới hạn request | (hệ thống) | Kiểm tra HMAC, timestamp, nonce, rate limit | FR-14 |
| UC-14 | Tính điểm theo rule | (hệ thống) | Quy đổi VND sang điểm và điểm sang VND, làm tròn xuống | FR-05, FR-08 |
| UC-15 | Ghi audit log | (hệ thống) | Ghi người thực hiện, hành động, đối tượng, kết quả, mã tương quan | FR-15 |
| UC-16 | Settlement lên Sui theo batch | Sui Blockchain | Gom sự kiện outbox, gửi lên Sui, lưu digest, đánh dấu SETTLED | FR-09, FR-10 |
| UC-17 | Xem dashboard hệ thống | Admin | Xem số tenant, số giao dịch, trạng thái settlement | FR-13 |
| UC-18 | Xem audit log | Admin | Xem nhật ký thao tác nhạy cảm | FR-13, FR-15 |
| UC-19 | Xem giao dịch settlement thất bại | Admin | Xem danh sách giao dịch FAILED để xử lý | FR-13 |
| UC-20 | Trả kết quả cũ theo Idempotency-Key | (hệ thống) | Cùng key, cùng nội dung thì trả kết quả ban đầu | FR-05 |
| UC-21 | Kiểm tra on-chain trước khi gửi lại | (hệ thống) | Khi timeout hoặc kết quả không rõ, kiểm tra Sui rồi mới retry | FR-09 |

### 3.2. Should và Could

| ID | Use case | Actor | Mức | FR |
|---|---|---|---|---|
| UC-22 | Truy vấn số dư qua MCP | AI Agent | Should | FR-17 |
| UC-23 | Truy vấn lịch sử qua MCP | AI Agent | Should | FR-17 |
| UC-24 | Truy vấn rule qua MCP | AI Agent | Should | FR-17 |
| UC-25 | Cấu hình rule nâng cao (hạn dùng, tier, multiplier) | Merchant | Should | FR-18 |
| UC-26 | Xem analytics | Merchant, Admin | Should | FR-19 |
| UC-27 | Quản lý hạn mức gas theo tenant | Admin | Should | FR-27 |
| UC-28 | Nhận webhook khi SETTLED | Merchant System | Should | FR-28 |
| UC-29 | Đối soát DB và chain | Admin, Sui Blockchain | Should | FR-29 |
| UC-30 | Quản lý nhân viên (STAFF) | Merchant | Could | FR-31 |
| UC-31 | Retry thủ công settlement FAILED | Admin | Could | FR-33 |
| UC-32 | Neo hash sổ cái lên Sui | Admin, Sui Blockchain | Could | FR-30 |
| UC-33 | MCP tool ghi (có xác nhận) | AI Agent | Could | FR-32 |

### 3.3. Quan hệ include và extend

| Use case gốc | Quan hệ | Use case kia | Điều kiện hoặc ý nghĩa |
|---|---|---|---|
| UC-06 Xoay vòng API key | include | UC-05 Tạo API key | Luôn tạo key mới |
| UC-06 Xoay vòng API key | include | UC-07 Thu hồi API key | Luôn thu hồi key cũ |
| UC-09, 10, 11, 12 (qua API) | include | UC-13 Xác thực và giới hạn request | Mọi request API đều được kiểm tra |
| UC-09, UC-10 | include | UC-14 Tính điểm theo rule | Issue tính điểm từ VND, redeem tính giá trị quy đổi |
| UC-04, 05, 07, 08 | include | UC-15 Ghi audit log | Mọi thao tác nhạy cảm đều được ghi |
| UC-20 | extend | UC-09, UC-10 | Chỉ khi Idempotency-Key đã được dùng với cùng nội dung |
| UC-21 | extend | UC-16 | Chỉ khi settlement bị timeout hoặc kết quả không xác định |

Mọi use case trong Portal (trừ UC-01) đều yêu cầu đã đăng nhập (UC-02). Điều này được ghi bằng chú thích trên sơ đồ để tránh vẽ quá nhiều mũi tên.

---

## 4. Đặc tả tóm tắt các use case chính

### UC-01. Đăng ký tenant

| Mục | Nội dung |
|---|---|
| Actor | Merchant |
| Tiền điều kiện | Chưa có tài khoản |
| Luồng chính | 1. Merchant nhập tên tenant, email, mật khẩu. 2. Hệ thống kiểm tra dữ liệu và email chưa tồn tại. 3. Hệ thống tạo tenant (PENDING) và tài khoản MERCHANT. 4. Hệ thống thông báo đang chờ duyệt. |
| Luồng thay thế | 2a. Dữ liệu không hợp lệ: HTTP 400. 2b. Email đã tồn tại: HTTP 409. |
| Hậu điều kiện | Tenant PENDING. Tenant chưa được issue hoặc redeem. |

### UC-04. Duyệt, từ chối, khóa tenant

| Mục | Nội dung |
|---|---|
| Actor | Admin |
| Tiền điều kiện | Admin đã đăng nhập (UC-02). Tenant tồn tại. |
| Luồng chính | 1. Admin chọn tenant. 2. Admin chọn duyệt, từ chối hoặc khóa. 3. Hệ thống đổi trạng thái. 4. Hệ thống ghi audit log (UC-15). |
| Luồng thay thế | 3a. Chuyển trạng thái không hợp lệ: HTTP 409. |
| Hậu điều kiện | Trạng thái tenant được cập nhật. Tenant bị từ chối hoặc khóa không giao dịch được. |

### UC-09. Issue điểm

| Mục | Nội dung |
|---|---|
| Actor | Merchant System |
| Tiền điều kiện | Tenant APPROVED. API key còn hiệu lực và có quyền ISSUE. Tenant đã có rule. |
| Luồng chính | 1. Gửi request gồm `customerId`, `orderId`, `amountVnd`, header `Idempotency-Key` và chữ ký HMAC. 2. Hệ thống xác thực request (UC-13). 3. Hệ thống kiểm tra Idempotency-Key chưa dùng. 4. Hệ thống tính điểm (UC-14). 5. Trong **một** transaction: tạo khách hàng nếu chưa có, cộng số dư, ghi ledger entry, cấp `chain_seq`, ghi outbox. 6. Trả kết quả kèm `settlementStatus` = PENDING. |
| Luồng thay thế | 2a. Sai chữ ký, timestamp lệch hoặc nonce đã dùng: HTTP 401. 2b. Vượt hạn mức: HTTP 429. 2c. Tenant chưa APPROVED: HTTP 403. 3a. Cùng key, cùng nội dung: trả kết quả cũ (UC-20). 3b. Cùng key, khác nội dung: HTTP 409. 4a. `amountVnd` không hợp lệ: HTTP 400. |
| Hậu điều kiện | Số dư tăng. Có một ledger entry. Có một dòng outbox PENDING. |

### UC-10. Redeem điểm

| Mục | Nội dung |
|---|---|
| Actor | Merchant System |
| Tiền điều kiện | Như UC-09, và khách hàng đã có điểm. API key có quyền REDEEM. |
| Luồng chính | 1. Gửi request gồm `customerId`, `orderId`, `pointsToRedeem`, `Idempotency-Key`, chữ ký HMAC. 2. Xác thực request (UC-13). 3. Trong **một** transaction: trừ điểm bằng cập nhật có điều kiện, ghi ledger entry, cấp `chain_seq`, ghi outbox. 4. Trả kết quả với số dư mới và `settlementStatus` = PENDING. |
| Luồng thay thế | 3a. Số dư không đủ: HTTP 422, không trừ điểm, không tạo ledger entry. Các lỗi khác như UC-09. |
| Hậu điều kiện | Số dư giảm đúng số điểm, không âm. Có một ledger entry và một dòng outbox PENDING. |

### UC-16. Settlement lên Sui theo batch

| Mục | Nội dung |
|---|---|
| Actor | Sui Blockchain (secondary). Được kích hoạt theo chu kỳ 5 đến 10 giây khi outbox có sự kiện chờ |
| Tiền điều kiện | Có sự kiện outbox ở trạng thái PENDING |
| Luồng chính | 1. Worker lấy sự kiện PENDING theo thứ tự `chain_seq` của từng khách hàng. 2. Gom thành batch. 3. Gửi qua Signer lên Sui. 4. Xác minh giao dịch thành công và lưu transaction digest. 5. Đánh dấu SETTLED. |
| Luồng thay thế | 3a. Lỗi hoặc timeout: kiểm tra on-chain (UC-21) rồi retry. 3b. Quá số lần retry: đánh dấu FAILED. 3c. Hết hạn mức gas (Should): giữ PENDING. |
| Hậu điều kiện | Sự kiện SETTLED kèm digest, hoặc FAILED kèm lý do. |

---

## 5. Ma trận truy vết FR và Use case

| FR | Use case |
|---|---|
| FR-01 | UC-01 |
| FR-02 | UC-04 |
| FR-03 | UC-05, UC-06, UC-07 |
| FR-04 | UC-02 |
| FR-05 | UC-09, UC-14, UC-20 |
| FR-06 | UC-10, UC-14 |
| FR-07 | UC-11, UC-12 |
| FR-08 | UC-08, UC-14 |
| FR-09 | UC-16, UC-21 |
| FR-10 | UC-16 |
| FR-11 | UC-09, UC-10, UC-11, UC-12 (qua SDK) |
| FR-12 | UC-03, UC-08, UC-11, UC-12 (qua Portal) |
| FR-13 | UC-17, UC-18, UC-19 |
| FR-14 | UC-13 |
| FR-15 | UC-15, UC-18 |

Mọi FR Must đều có ít nhất một use case.

---

## 6. Giả định

| # | Nội dung |
|---|---|
| A1 | End-user chưa phải actor trong MVP (xem mục 2) |
| A2 | Hai vai trò MVP: ADMIN và MERCHANT. STAFF là Could (UC-30) |
| A3 | UC-13, UC-14, UC-15, UC-20, UC-21 là use case mức hệ thống để thể hiện include và extend. Nếu giảng viên muốn chỉ giữ use case có mục tiêu người dùng, chuyển chúng thành ghi chú trong đặc tả |
| A4 | UC-16 dùng Sui Blockchain làm actor và có trigger theo thời gian. Có thể vẽ thêm actor Timer nếu giảng viên yêu cầu |
| Q1 | Merchant xem số dư và lịch sử trên Portal (UC-11, 12) dùng chung use case với Merchant System. Nếu muốn tách để đặc tả riêng, bổ sung UC mới |
