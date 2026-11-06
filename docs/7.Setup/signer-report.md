# Báo cáo kỹ thuật: Xây dựng Sui Signer Service cho OctaPoint

| Mục | Nội dung |
|---|---|
| Ticket | SCRUM-17 / SCRUM-18 (parent SCRUM-16) |
| Repository | LAP_TRINH_JAVA |
| Nhánh | `dev-hoangminh` |
| Ngày | 2026-11-03 |

---

## 1. Mục tiêu ticket

Xây dựng **TypeScript Sui Signer service** — microservice nội bộ thực hiện:

1. Nhận request nội bộ từ backend/worker qua HTTP.
2. Ký giao dịch Move bằng Ed25519 keypair.
3. Gửi transaction lên **Sui Testnet** qua gRPC.
4. Gọi Move function `octapoint::issue` trên smart contract đã deploy.
5. Trả kết quả gồm **transaction digest** và **trạng thái settlement** (`SETTLED` / lỗi).

Sui package đã deploy trên Testnet:

| Object | ID |
|---|---|
| Package | `0x908b5905ece899ac50024a2d33d96cd2465608b44a338689d778a6480fd5c17b` |
| AdminCap | `0xab12c867037151d10d00ee14acd114fbf0ee6d4488cab42f936c1fbcc2834fbc` |
| Shared Balance | `0x76d11e3a5a2ed8d3b78aa7592ba6fe1902d094be4ee0119ed0c7ea051158c0c7` |

---

## 2. Các file đã tạo hoặc thay đổi

| File | Vai trò |
|---|---|
| `signer/package.json` | Khai báo project Node.js, dependencies, scripts (build/dev/start/test) |
| `signer/tsconfig.json` | Cấu hình TypeScript compiler: target ES2022, module NodeNext, strict mode |
| `signer/.env.example` | Template biến môi trường (không chứa secret thật) |
| `signer/src/config.ts` | Load và validate biến môi trường, export cấu hình dạng typed object |
| `signer/src/sui.ts` | Khởi tạo `SuiGrpcClient`, Ed25519 keypair; hàm `issue()` xây dựng và gửi transaction |
| `signer/src/server.ts` | Express HTTP server: middleware auth, endpoint `/health` và `/internal/issue` |
| `.gitignore` | Thêm `signer/node_modules/`, `signer/dist/`, `signer/.env` vào danh sách ignore |
| `contracts/octapoint_move/sources/octapoint.move` | Smart contract Move: struct `AdminCap`, `Balance`; function `issue`, `balance` |

---

## 3. Dependency và cấu hình

### 3.1 Dependencies

| Package | Phiên bản | Mục đích |
|---|---|---|
| `@mysten/sui` | ^2.38.0 | SDK chính thức của Sui: gRPC client, keypair, transaction builder |
| `express` | ^5.3.0 | HTTP server framework |
| `dotenv` | ^18.0.9 | Load biến môi trường từ `.env` |
| `typescript` | ^6.0.3 | TypeScript compiler (devDependency) |
| `tsx` | ^4.23.15 | Chạy TypeScript trực tiếp cho dev mode (devDependency) |
| `@types/express` | ^5.0.6 | Type definitions cho Express (devDependency) |
| `@types/node` | ^26.6.6 | Type definitions cho Node.js (devDependency) |

### 3.2 Scripts

```json
{
  "test":  "tsc --noEmit",
  "build": "tsc",
  "dev":   "tsx watch src/server.ts",
  "start": "node dist/server.js"
}
```

| Script | Mô tả |
|---|---|
| `npm test` | Type-check toàn bộ source, không emit file |
| `npm run build` | Biên dịch TypeScript → JavaScript trong `dist/` |
| `npm run dev` | Chạy server với hot-reload (development) |
| `npm start` | Chạy bản build production từ `dist/` |

### 3.3 Biến môi trường

| Biến | Bắt buộc | Mô tả |
|---|---|---|
| `PORT` | Không (mặc định `3001`) | Port HTTP server |
| `SUI_RPC_URL` | Không (mặc định `https://fullnode.testnet.sui.io:443`) | URL endpoint Sui |
| `SUI_PRIVATE_KEY` | **Có** | Private key dạng Bech32 (`suiprivkey1...`) |
| `SUI_PACKAGE_ID` | **Có** | Object ID của Move package đã deploy |
| `SUI_MODULE_NAME` | Không (mặc định `octapoint`) | Tên module Move |
| `SUI_ADMIN_CAP_ID` | **Có** | Object ID của `AdminCap` |
| `SUI_BALANCE_ID` | **Có** | Object ID của shared `Balance` |
| `SERVICE_TOKEN` | **Có** | Token xác thực nội bộ cho header `X-Service-Token` |

> ⚠️ **`SUI_PRIVATE_KEY` và `SERVICE_TOKEN` là secret — không được commit vào source code.** File `signer/.env` đã nằm trong `.gitignore`.

---

## 4. API đã triển khai

### 4.1 `GET /health`

Kiểm tra trạng thái service.

- **Header bắt buộc:** `X-Service-Token: <token>`
- **Response 200:**
  ```json
  {
    "status": "UP",
    "address": "0xcf651235dad1649647b3b39563e1bebbc16f27e5861ea96544d3dacf3d928996"
  }
  ```

### 4.2 `POST /internal/issue`

Gọi Move function `octapoint::issue` để cộng points trên chain.

- **Header bắt buộc:** `X-Service-Token: <token>`
- **Request body:**
  ```json
  { "amount": 10 }
  ```
- **Response 200 (thành công):**
  ```json
  {
    "digest": "FtqPENZnom8iAfpFUS4oueYhXgp2hfoFkokddvABZYZ4",
    "status": "SETTLED",
    "gasUsed": { ... }
  }
  ```

### 4.3 HTTP Status codes

| Status | Điều kiện |
|---|---|
| `200` | Transaction thành công |
| `400` | Body không hợp lệ hoặc `amount` không phải positive safe integer |
| `401` | Thiếu hoặc sai `X-Service-Token` |
| `502` | Giao dịch Sui thất bại (lỗi on-chain) |

---

## 5. Các lỗi đã gặp và cách xử lý

| # | Lỗi | Nguyên nhân | Giải pháp |
|---|---|---|---|
| 1 | `SuiClient` không tồn tại | SDK `@mysten/sui@2.38.0` đã loại bỏ `SuiClient` (JSON-RPC) | Chuyển sang `SuiGrpcClient` từ `@mysten/sui/grpc` |
| 2 | JSON-RPC public fullnode deprecated | Sui đã migrate sang gRPC protocol | Dùng `SuiGrpcClient` thay cho `SuiHTTPClient`/`SuiClient` |
| 3 | `SuiGrpcClient` yêu cầu `baseUrl` | Constructor khác với `SuiClient` cũ | Cấu hình `{ network: 'testnet', baseUrl: config.rpcUrl }` |
| 4 | API gRPC dùng `include` | Khác với `options: { showEffects: true }` cũ | Đổi sang `include: { effects: true, events: true }` |
| 5 | Kết quả gRPC trả `result.Transaction` | Khác với `result.effects` trực tiếp của JSON-RPC | Truy cập `result.Transaction.digest` và `result.Transaction.effects` |
| 6 | `invalid data length` khi import key | Private key sai định dạng (thiếu prefix `suiprivkey1`) | Sử dụng `decodeSuiPrivateKey()` decode Bech32 trước khi tạo keypair |
| 7 | `Unauthorized` | Service token trong `.env` không khớp header | Kiểm tra giá trị `SERVICE_TOKEN` trong `.env` |
| 8 | PowerShell biến dạng JSON `{amount:10}` | `curl` trên PowerShell xử lý quotes khác bash | Dùng `Invoke-RestMethod` với `-Body (ConvertTo-Json @{amount=10})` |

---

## 6. Quy trình chạy local

### 6.1 Chuẩn bị

```powershell
cd d:\Y3\java\project\LAP_TRINH_JAVA\signer

# Copy và chỉnh sửa biến môi trường
Copy-Item .env.example .env
# Mở .env và điền SUI_PRIVATE_KEY, SERVICE_TOKEN thật

# Cài dependencies
npm install
```

### 6.2 Kiểm tra TypeScript

```powershell
npm test
# Kết quả: tsc --noEmit, không có error
```

### 6.3 Build

```powershell
npm run build
# Kết quả: tsc biên dịch thành công, output vào dist/
```

### 6.4 Chạy server (development)

```powershell
npm run dev
# Output: Signer listening on port 3001
```

### 6.5 Test API

```powershell
# Lưu service token vào biến
$token = "giá-trị-SERVICE_TOKEN-trong-env"

# Health check
Invoke-RestMethod -Uri http://localhost:3001/health `
  -Headers @{ "X-Service-Token" = $token }

# Issue 10 points
Invoke-RestMethod -Uri http://localhost:3001/internal/issue `
  -Method POST `
  -ContentType "application/json" `
  -Headers @{ "X-Service-Token" = $token } `
  -Body (ConvertTo-Json @{ amount = 10 })
```

---

## 7. Kết quả kiểm thử thực tế

| Kiểm tra | Kết quả |
|---|---|
| `npm test` (tsc --noEmit) | ✅ Pass, không có error |
| `npm run build` | ✅ Biên dịch thành công |
| `GET /health` | ✅ Trả `{ "status": "UP", "address": "0xcf65…8996" }` |
| Signer address | `0xcf651235dad1649647b3b39563e1bebbc16f27e5861ea96544d3dacf3d928996` |
| `POST /internal/issue` với `{ "amount": 10 }` | ✅ Giao dịch thành công |
| Settlement status | `SETTLED` |
| Transaction digest | `FtqPENZnom8iAfpFUS4oueYhXgp2hfoFkokddvABZYZ4` |
| On-chain Balance | Tăng thêm 10 (xác nhận trên Sui Testnet Explorer) |
| Token auth sai → 401 | ✅ Trả `{ "error": "Unauthorized" }` |
| Amount ≤ 0 → 400 | ✅ Trả `{ "error": "amount must be a positive safe integer" }` |

---

## 8. Bảo mật

- **Không ghi private key** (`SUI_PRIVATE_KEY`) vào source code, docs, hoặc log.
- **Không commit** file `signer/.env` — đã thêm vào `.gitignore`.
- **Không gửi** private key hoặc recovery phrase lên chat, ticket, hoặc PR.
- **Không log secret**: error handler chỉ log message, không log stack chứa config.
- **Endpoint chỉ dùng nội bộ**: `POST /internal/issue` không expose ra public.
- **Yêu cầu `X-Service-Token`**: mọi request phải có header này, middleware kiểm tra trước khi vào route handler.
- **Không trả lỗi Sui chi tiết ra client**: response 502 chỉ trả `"Sui transaction failed"`, chi tiết chỉ log ở server console.

---

## 9. Acceptance criteria

- [x] Signer service khởi động thành công (`npm run dev`).
- [x] Health endpoint hoạt động (`GET /health` → 200, status `UP`).
- [x] Service-token authentication hoạt động (thiếu/sai → 401).
- [x] TypeScript compile pass (`npm test` → không lỗi).
- [x] Sui gRPC client kết nối Testnet thành công.
- [x] Move `issue` transaction thực thi thành công on-chain.
- [x] Transaction trả digest (`FtqPENZnom8iAfpFUS4oueYhXgp2hfoFkokddvABZYZ4`).
- [x] Settlement status là `SETTLED`.
- [x] On-chain Balance tăng đúng 10.
- [x] Secret không commit (`.gitignore` bao gồm `signer/.env`).

---

## 10. Phần còn lại / chưa triển khai

| Hạng mục | Trạng thái |
|---|---|
| Tích hợp trực tiếp với Spring Boot Outbox Worker | ❌ Chưa triển khai |
| Thêm signer vào `docker-compose.yml` | ❌ Chưa triển khai |
| Batch transaction (gom nhiều issue/redeem trong 1 tx) | ❌ Chưa triển khai |
| Idempotency theo `eventId` | ❌ Chưa triển khai |
| Retry/backoff khi Sui transaction thất bại | ❌ Chưa triển khai |
| Kiểm tra on-chain sau timeout | ❌ Chưa triển khai |
| Redeem transaction (`octapoint::redeem`) | ❌ Chưa triển khai (Move contract chưa có hàm redeem) |
| Sequence protection trong Move contract | ❌ Chưa triển khai |
| Integration test tự động với Sui Testnet | ❌ Chưa triển khai |

---

## 11. Hướng phát triển tiếp theo

1. **Mở rộng request schema**: thêm `eventId`, `tenantId`, `customerId`, `points`, `chainSeq` vào body `/internal/issue`.
2. **Idempotency**: lưu `eventId` đã xử lý (Redis hoặc PostgreSQL) để tránh gửi duplicate transaction.
3. **Tích hợp Spring Boot Outbox Worker**: worker poll outbox table, gọi signer HTTP API, cập nhật settlement status.
4. **Retry/backoff**: khi Sui trả lỗi tạm thời (rate limit, network timeout), retry với exponential backoff.
5. **On-chain verification**: sau timeout, query digest trên Sui để xác nhận transaction status.
6. **Docker service**: thêm `signer` vào `docker-compose.yml` với Dockerfile riêng.
7. **Test**: unit test (mock `SuiGrpcClient`), API test (supertest), integration test (Sui Testnet).
8. **Nâng cấp Move contract**: thêm `redeem`, `chainSeq` protection, emit events cho audit.

---

## 12. Kết luận

**Ticket SCRUM-17 / SCRUM-18 hoàn thành mục tiêu cốt lõi:**

Sui Signer service đã được xây dựng, triển khai, và kiểm thử thành công trên Sui Testnet. Service nhận request nội bộ qua HTTP, ký transaction bằng Ed25519 keypair, gọi Move function `octapoint::issue` qua gRPC, và trả kết quả digest cùng trạng thái settlement `SETTLED`. Giao dịch thực tế đã xác nhận on-chain, Balance tăng đúng giá trị yêu cầu.

Các phần chưa triển khai (idempotency, retry, outbox integration, redeem, docker service) là scope của các ticket tiếp theo trong SCRUM-16.

| Trạng thái | Mô tả |
|---|---|
| ✅ Core Signer | Hoạt động, đã kiểm thử on-chain |
| ✅ Smart contract | Deploy thành công trên Testnet |
| ⏳ Tích hợp backend | Chờ outbox worker + docker service |
| ⏳ Production-ready | Chờ idempotency, retry, redeem, test tự động |
