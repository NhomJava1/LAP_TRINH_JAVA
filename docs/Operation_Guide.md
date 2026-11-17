# Hướng dẫn Vận hành & Kịch bản Demo End-to-End — OctaPoint MVP

Tài liệu này mô tả cách khởi chạy toàn bộ hệ thống **OctaPoint (Credit-as-a-Service trên Sui Blockchain)** và kịch bản demo 9 bước đầy đủ từ Portal đến SDK và Sui Testnet Explorer.

---

## 1. Các dịch vụ và cổng mặc định

| Dịch vụ | Công nghệ | URL / Port | Vai trò |
|---|---|---|---|
| **Frontend Portal** | Next.js 16 / React 19 | http://localhost:3000 | Cổng Merchant & System Admin |
| **Backend Core** | Spring Boot 3.3.4 / Java 21 | http://localhost:8080 | API nghiệp vụ, Auth, HMAC, Ledger, Outbox Worker |
| **Swagger / OpenAPI** | springdoc-openapi | http://localhost:8080/swagger-ui.html | Tài liệu API tương tác |
| **Signer Service** | Node.js / TypeScript (`@mysten/sui`) | http://localhost:3001 | Ký và gửi giao dịch lên Sui Testnet |
| **PostgreSQL 15** | Docker Compose (`octapoint_postgres`) | `localhost:5432` | Nguồn dữ liệu có thẩm quyền (SSOT) |
| **Redis 7** | Docker Compose (`octapoint_redis`) | `localhost:6379` | Lưu HMAC nonce chống replay attack |

---

## 2. Khởi chạy toàn bộ hệ thống

### Bước 1: Chuẩn bị môi trường
- Kiểm tra file `.env` tại thư mục gốc (hoặc copy từ `.env.example`):
  - `SETTLEMENT_MODE=LIVE` để settlement thật lên Sui Testnet (hoặc `SETTLEMENT_MODE=MOCK` để chạy mô phỏng khi mất mạng/Testnet gián đoạn).
- Nếu chạy chế độ `LIVE`, đảm bảo file `signer/.env` (không commit vào Git) đã có `SUI_PRIVATE_KEY`, `SUI_PACKAGE_ID`, `SUI_ADMIN_CAP_ID`, `SUI_BALANCE_ID` hợp lệ và ví Testnet có SUI gas.

### Bước 2: Khởi động Backend Stack bằng Docker Compose
```powershell
docker compose up -d --build postgres redis signer core
```
Kiểm tra sức khỏe Backend:
```powershell
curl http://localhost:8080/actuator/health
```

### Bước 3: Khởi động Frontend Portal
```powershell
cd frontend
npm ci
npm run dev
```
Mở trình duyệt tại http://localhost:3000.

---

## 3. Tài khoản Admin mặc định (Seed tự động khi khởi động)
- **Email:** `admin@octapoint.io`
- **Mật khẩu:** `admin123`

---

## 4. Kịch bản Demo 9 bước (10 phút)

| Bước | Vai trò | Thao tác trên hệ thống | Kết quả kiểm chứng |
|---|---|---|---|
| **1** | Merchant | Vào `/register`, đăng ký doanh nghiệp **"Siêu thị A"** | Tenant tạo thành công ở trạng thái `PENDING` |
| **2** | Admin | Đăng nhập `/login` bằng `admin@octapoint.io`, vào `/admin/tenants`, bấm **Duyệt** | Tenant chuyển sang `APPROVED`, ghi vào Audit Log |
| **3** | Merchant | Đăng nhập tài khoản Merchant, vào `/merchant/api-keys` tạo API Key + vào `/merchant/rule-config` lưu `1000 VND = 1 điểm` | Secret chỉ hiển thị 1 lần; rule được lưu PostgreSQL |
| **4** | Developer / SDK | Gọi `client.issue` cho khách `C1` với đơn hàng `100.000 VND` | Cấp `100` điểm, `settlementStatus = PENDING`, `chainSeq = 0` |
| **5a** | Tester / SDK | Gửi lại request với **cùng `Idempotency-Key`**, timestamp/nonce mới | Trả về đúng `transactionId` cũ, số dư giữ nguyên `100` |
| **5b** | Tester / SDK | Phát lại **đúng nguyên** request đã ký (cùng `X-OP-Nonce`) | Bị chặn `HTTP 401 AUTH_NONCE_REUSED` |
| **5c** | Tester / SDK | Sửa chữ ký `X-OP-Signature` sai | Bị chặn `HTTP 401 AUTH_INVALID_SIGNATURE`, ghi Audit Log |
| **6** | Developer / SDK | Gọi `redeem(30)` rồi gọi `redeem(100)` | `redeem(30)` thành công còn `70`; `redeem(100)` bị từ chối `HTTP 422 INSUFFICIENT_BALANCE` |
| **7** | Merchant | Mở `/merchant/history`, bấm **Làm mới trạng thái** | Thấy số dư `70`, lịch sử `ISSUE` & `REDEEM` chuyển từ `PENDING` → `SETTLED` |
| **8** | Cả nhóm | Bấm vào link `txDigest` trên giao diện | Mở Sui Testnet Explorer hiển thị transaction thực tế |
| **9** | Admin | Mở `/admin` và `/admin/audit-logs` | Thấy tổng số tenant, giao dịch, trạng thái settlement & lịch sử bảo mật |
| **10** | AI / MCP | Tạo API Key scope `READ` tại `/merchant/api-keys`, chạy `cd mcp-server && npm run demo` | AI Assistant truy vấn được số dư/lịch sử/rule qua MCP; lệnh ghi bằng key `READ` bị chặn `403 INSUFFICIENT_SCOPE` |

---

## 5. Chạy kịch bản Demo tự động bằng TypeScript SDK & MCP Server

Sau khi chạy Docker Compose (`postgres`, `redis`, `signer`, `core`), bạn có thể chạy toàn bộ 9 bước + Bước 10 qua CLI:

```powershell
cd sdk-ts
npm install
npm run demo
```

Chạy demo Bước 10 (AI Assistant qua MCP Server read-only `FR-17`):
```powershell
cd mcp-server
npm install
npm run demo
```

Hoặc truyền API key có sẵn từ Portal:
```powershell
$env:API_KEY="ak_..."
$env:API_SECRET="sec_..."
npm run demo
```

### Tính năng mở rộng Should & Could trên Portal & Admin
- **Merchant Portal**:
  - `/merchant/analytics`: Thống kê tích/đổi điểm theo ngày (`FR-19`) & cảnh báo gian lận (`FR-22`).
  - `/merchant/rule-config`: Hạn sử dụng điểm & hệ số nhân hạng Silver / Gold (`FR-18`).
  - `/merchant/history`: Kết nối realtime Server-Sent Events (`/api/portal/events/stream`, `FR-23`).
  - `/merchant/settings`: Cấu hình HMAC Webhook (`FR-28`), tài khoản `STAFF` chỉ đọc (`FR-31`), gói cước SaaS & hóa đơn tạm tính (`FR-20`).
- **Admin Portal**:
  - `/admin`: Xem Micrometer metrics (`NFR-06`), chạy **Đối soát On-Chain vs DB** (`POST /api/admin/reconcile`, `FR-29`) và **Neo Hash Ledger lên Sui** (`POST /api/admin/anchors`, `FR-30`).
  - `/admin/tenants`: Điều chỉnh **Gas Quota (MIST)** cho từng tenant và tự động mở khóa các event `PAUSED_GAS_QUOTA` (`FR-27`).

---

## 6. Chạy kiểm thử tự động (CI & Local)

- **Backend (Unit + ArchUnit + Integration + Should/Could + NFR-03 Concurrency):**
  ```powershell
  docker run --rm -v octapoint-m2:/root/.m2 -v "${PWD}/backend:/workspace" -w /workspace maven:3.9-eclipse-temurin-21 mvn --batch-mode verify
  ```
- **Java 21 SDK (`FR-25`):**
  ```powershell
  docker run --rm -v octapoint-m2:/root/.m2 -v "${PWD}/sdk-java:/workspace" -w /workspace maven:3.9-eclipse-temurin-21 mvn --batch-mode test
  ```
- **TypeScript SDK (`FR-26`, `FR-28`):** `cd sdk-ts && npm test && npm run build`
- **MCP Server (`FR-17`, `FR-32`):** `cd mcp-server && npm test && npm run build`
- **Sui Signer (`FR-26`, `FR-29`, `FR-30`):** `cd signer && npm test && npm run build`
- **Move Contract:**
  ```powershell
  docker run --rm -v sui-config:/root/.sui -v "${PWD}:/workspace" -w /workspace/contracts/octapoint_move lap_trinh_java-sui-cli move test
  ```
- **Frontend:** `cd frontend && npm run lint && npm run build`
- **Load Test hiệu năng `NFR-01` (20 VU × 1.000 request/endpoint, yêu cầu Docker stack đang chạy):**
  ```powershell
  cd sdk-ts
  npm run benchmark
  ```

---

## 7. Danh mục Tài liệu Hệ thống (Task Package 5)

| Tài liệu | Nội dung |
|---|---|
| [SDK_and_MCP_Guide.md](./SDK_and_MCP_Guide.md) | Hướng dẫn HMAC ADR-0003, TypeScript SDK, Java 21 SDK và MCP Server (`FR-17`, `FR-32`) |
| [Database_and_Move_Specification.md](./Database_and_Move_Specification.md) | Đặc tả PostgreSQL Flyway `V1`–`V5` và Move Smart Contract `octapoint.move` |
| [Test_Plan_and_Report.md](./Test_Plan_and_Report.md) | Kế hoạch kiểm thử, ma trận xác minh FR/NFR và báo cáo Load Test `NFR-01` |
| [User_Manual.md](./User_Manual.md) | Hướng dẫn sử dụng Portal cho Merchant Owner, Staff và System Admin |
| [Future_Work_Architecture.md](./Future_Work_Architecture.md) | Kiến trúc định hướng `v2.0` (`FR-16` zkLogin/Enoki, `FR-21` Walrus/Seal, `FR-24` ClickHouse CDC, Mainnet) |
