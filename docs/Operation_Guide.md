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

---

## 5. Chạy kịch bản Demo tự động bằng TypeScript SDK

Sau khi chạy Docker Compose (`postgres`, `redis`, `signer`, `core`), bạn có thể chạy toàn bộ 9 bước qua CLI:

```powershell
cd sdk-ts
npm install
npm run demo
```

Hoặc truyền API key có sẵn từ Portal:
```powershell
$env:API_KEY="ak_..."
$env:API_SECRET="sec_..."
npm run demo
```

---

## 6. Chạy kiểm thử tự động (CI & Local)

- **Backend (Unit + ArchUnit + Integration + NFR-03 Concurrency):**
  ```powershell
  docker run --rm -v octapoint-m2:/root/.m2 -v "${PWD}/backend:/workspace" -w /workspace maven:3.9-eclipse-temurin-21 mvn --batch-mode verify
  ```
- **TypeScript SDK:** `cd sdk-ts && npm test && npm run build`
- **Sui Signer:** `cd signer && npm test && npm run build`
- **Move Contract:**
  ```powershell
  docker run --rm -v sui-config:/root/.sui -v "${PWD}:/workspace" -w /workspace/contracts/octapoint_move lap_trinh_java-sui-cli move test
  ```
- **Frontend:** `cd frontend && npm run build && npm run lint`
