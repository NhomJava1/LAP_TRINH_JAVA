# Test Plan & Verification Report — OctaPoint (`NFR-01`–`NFR-06` & `FR-01`–`FR-33`)

Tài liệu tổng hợp chiến lược kiểm thử, ma trận xác minh yêu cầu theo [srs-v1.0.md](./1.SRS/srs-v1.0.md), kết quả kiểm thử tự động đa tầng, bằng chứng Sui Testnet Explorer và **Báo cáo Load Test hiệu năng chính thức `NFR-01` (`20 VU × 1.000 request/endpoint`)**.

---

## 1. Chiến lược & Các Tầng Kiểm thử

| Tầng kiểm thử | Công cụ / Framework | Phạm vi bao phủ |
|---|---|---|
| **Static Architecture Check** | `ArchUnit` (`ArchitectureTest`) | Ràng buộc module boundary `..tenant..`, ép mọi `ChainClient` nằm trong `..chain..`, cấm rò rỉ `MockChainClient` ra ngoài |
| **Cross-Language Crypto & Unit Tests** | `JUnit 5`, Node `tsx --test` | Canonicalize RFC 3986, HMAC-SHA256 test vector chung giữa Java Core, Java 21 SDK và TypeScript SDK; Offline Queue; MCP schema |
| **Backend Integration & Concurrency** | Spring Boot `@SpringBootTest` + `@AutoConfigureMockMvc` + H2 PostgreSQL Mode | Đăng ký/duyệt Tenant, xoay vòng/thu hồi API Key, Scope enforcement, Idempotency, Replay Nonce, RBAC (`ADMIN`/`MERCHANT`/`STAFF`), **20 luồng đồng thời `SELECT FOR UPDATE` (`NFR-03`)**, Rule Tier/Expiry (`FR-18`), Webhook HMAC (`FR-28`), Gas Quota (`FR-27`), Reconcile (`FR-29`), Ledger Anchor (`FR-30`), Metrics (`NFR-06`) |
| **Smart Contract Move Tests** | `sui move test` | `octapoint_move::octapoint` init, `issue`, `redeem`, `settle_issue`, `settle_redeem`, chặn trừ âm số dư (`E_INSUFFICIENT_BALANCE`) và sai `seq` (`E_SEQ_MISMATCH`) |
| **AI MCP Protocol E2E** | `@modelcontextprotocol/sdk` `InMemoryTransport` | Kiểm tra mặc định chỉ expose 4 read tools (`FR-17`), chế độ write yêu cầu xác nhận 2 bước (`FR-32`) và `403 INSUFFICIENT_SCOPE` với key `READ` |
| **Frontend Static & Build Check** | ESLint + Next.js 16 Turbopack + TypeScript | Kiểm tra kiểu dữ liệu, React hooks lint và render build 15 routes |
| **Live Docker + Sui Testnet E2E** | Docker Compose (`PostgreSQL 15` + `Redis 7` + `signer` + `core`) | Chạy kịch bản 9 bước (`sdk-ts npm run demo`), bước 10 MCP (`mcp-server npm run demo`), live Admin `/reconcile` và `/anchors` trên Sui Testnet |
| **Performance / Load Benchmark (`NFR-01`)** | `sdk-ts/examples/benchmark-nfr01.ts` | `20` Virtual Users chạy đồng thời, `420` requests warm-up + `1.000` requests đo thực tế cho từng endpoint `/issue`, `/redeem`, `/balance` |

---

## 2. Kết quả Kiểm thử Tự động (Verified Test Runs)

| Module | Lệnh kiểm thử | Kết quả | Chi tiết |
|---|---|---|---|
| **Backend Core** | `mvn --batch-mode verify` *(Docker Maven 3.9 / Temurin 21)* | **PASS (`9/9`)** | `MockChainClientTest` (`2/2`), `MvpFlowAndConcurrencyTest` (`1/1` — bao gồm `NFR-03` 20 luồng đồng thời), `HmacCodecTest` (`2/2`), `ShouldAndCouldExtensionsTest` (`1/1`), `ArchitectureTest` (`3/3`) |
| **Java 21 SDK** | `mvn --batch-mode test` *(Docker Maven 3.9 / Temurin 21)* | **PASS (`3/3`)** | Cross-language HMAC vector match, Webhook signature verify, HTTP client + `403 INSUFFICIENT_SCOPE` handling |
| **TypeScript SDK** | `cd sdk-ts && npm test && npm run build` | **PASS (`4/4`)** | Canonicalize RFC 3986, HMAC shared test vector, `verifyWebhookSignature`, `OfflineCreditQueue` FIFO retry |
| **MCP Server** | `cd mcp-server && npm test && npm run build` | **PASS (`2/2`)** | Read-only 4 tools mặc định (`FR-17`), 2-step confirmation write (`FR-32`) & chặn replay token |
| **Sui Signer** | `cd signer && npm test && npm run build` | **PASS** | `tsc --noEmit` & `tsc` exit `0` |
| **Frontend Portal** | `cd frontend && npm run lint && npm run build` | **PASS** | ESLint `0` errors; TypeScript & Next.js build `15/15` routes (chạy lại qua one-time Next.js `16.4.2` security nudge) |
| **Sui Move Contract** | `sui move test` *(trong container CLI)* | **PASS (`4/4`)** | Kiểm thử `issue`, `redeem`, `settle_issue`, `settle_redeem` trên module `octapoint_move::octapoint` |

---

## 3. Bằng chứng Sui Testnet Live Explorer (`NFR-05`, `FR-29`, `FR-30`)

Chạy live với `SETTLEMENT_MODE=LIVE` qua ví Signer `0xcf651235dad1649647b3b39563e1bebbc16f27e5861ea96544d3dacf3d928996`:

| Loại giao dịch | Kết quả / Digest xác minh trên Sui Explorer Testnet |
|---|---|
| `ISSUE` (100 pts, `chainSeq = 0`) | `8mAp9X2rwNDCbeMNEsbGXM54e9vBBUnjyMCEe33z4UGy` (`SETTLED` / `LIVE`) |
| `REDEEM` (30 pts, `chainSeq = 1`) | `4hPYsBtKZ8RVLFvC2e8L1wQ4WPpvzY9dTLKB4zpviHju` (`SETTLED` / `LIVE`) |
| `POST /api/admin/anchors` (`FR-30`) | `BV6cre1JWzKZ9AVXp9u1NfGi7PDDX1yGrwnzMwbQnAqC` (`ANCHORED`, SHA-256 `b1048222273f937d0418b4161d5e2b45263e483330fb7b6c9a7510495ce982a7`) |
| `POST /api/admin/reconcile` (`FR-29`) | Status `MATCHED_WITH_HISTORY` (`chainBalanceValue = 905`, `dbSettledIssuePoints = 650`) |

---

## 4. Báo cáo Load Test Hiệu năng `NFR-01` (`Sub-100ms p95`)

### 4.1. Môi trường & Cấu hình Đo (`SRS 5.1`)
- **CPU**: `12th Gen Intel(R) Core(TM) i5-12450HX` (8 Cores / 12 Logical Processors)
- **RAM**: `23.71 GB`
- **OS**: `Microsoft Windows 11 Home Single Language` (`Version 10.0.22631`)
- **Container Runtime**: `Docker version 29.4.3`, `docker compose` chạy 4 service (`postgres:15-alpine`, `redis:7-alpine`, `octapoint_signer`, `lap_trinh_java-core-1`) với profile `SPRING_PROFILES_ACTIVE=prod` (`application-prod.yaml`: HikariCP `maximum-pool-size: 32`, `show-sql: false`, `open-in-view: false`, Flyway up to `v6`)
- **Kịch bản đo**: `cd sdk-ts && npm run benchmark` ([benchmark-nfr01.ts](../sdk-ts/examples/benchmark-nfr01.ts)):
  - `20` concurrent Virtual Users (`VUS = 20`)
  - `1` benchmark tenant (`gasQuotaMist = 0` để cô lập hoàn toàn thời gian off-chain API khỏi Sui Testnet RPC theo `SRS 5.1`), `40` API keys HMAC-SHA256 (`4.800 req/phút` headroom dưới `RateLimitFilter` thật)
  - `200` customer IDs phân vùng cho `20` VU (`10` customer/VU)
  - **Warm-up**: `420` requests (`1.920 ms`) không tính vào số liệu đo
  - **Measured Run**: `1.000` requests cho mỗi endpoint (`3.000` requests tổng cộng)

### 4.2. Số liệu Baseline → Tối ưu → Kết quả Cuối cùng

Quá trình đo & tối ưu nội bộ được ghi nhận minh bạch qua 3 lượt đo thật trên cùng máy:

| Phiên bản tối ưu | `POST /issue` `p95` (RPS) | `POST /redeem` `p95` (RPS) | `GET /balance` `p95` (RPS) |
|---|---:|---:|---:|
| **Baseline** *(trước tối ưu hot-path)* | `114.16 ms` (263.55 RPS) | `104.23 ms` (333.67 RPS) | `51.49 ms` (603.78 RPS) |
| **Pass 1** *(V6 indexes, lazy `CustomerEnsureService`, `@Async` POS audit, API Key cache)* | `100.13 ms` (314.31 RPS) | `81.74 ms` (383.84 RPS) | `69.13 ms` (502.64 RPS) |
| **Final** *(+ `Persistable<String>` bỏ `em.merge` pre-SELECT + Write-Through `TenantService` Cache)* | **`69.18 ms` (421.23 RPS)** | **`66.70 ms` (473.86 RPS)** | **`36.85 ms` (844.16 RPS)** |

### 4.3. Chi tiết Phiên đo Đạt chuẩn Cuối cùng (`0` Errors — 100% `< 100 ms` p95)

| Endpoint | Count | 200 OK | Errors | Total (ms) | RPS | Min (ms) | Avg (ms) | p50 (ms) | **p95 (ms)** | p99 (ms) | Max (ms) | Đạt `NFR-01` |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|:---:|
| `POST /api/merchant/v1/credits/issue` | 1,000 | 1,000 | 0 | 2,373.99 | 421.23 | 15.06 | 45.93 | 44.96 | **69.18** | 81.80 | 98.89 | **PASS (`< 100 ms`)** |
| `POST /api/merchant/v1/credits/redeem` | 1,000 | 1,000 | 0 | 2,110.32 | 473.86 | 11.93 | 40.60 | 38.83 | **66.70** | 82.54 | 108.38 | **PASS (`< 100 ms`)** |
| `GET /api/merchant/v1/credits/balance` | 1,000 | 1,000 | 0 | 1,184.61 | 844.16 | 6.74 | 22.66 | 21.55 | **36.85** | 46.05 | 59.32 | **PASS (`< 100 ms`)** |

**Các tối ưu kỹ thuật đã giúp giảm `p95` của `/issue` từ `114.16 ms` xuống `69.18 ms` (-39.4%)**:
1. Triển khai `Persistable<String>` trên `LedgerEntry`, `OutboxEvent`, `IdempotencyRecord` và `AuditLog` để Spring Data JPA gọi thẳng `em.persist()` thay vì `em.merge()`, loại bỏ 3–4 truy vấn `SELECT ... WHERE id = ?` thừa trong mỗi transaction ghi.
2. Tạo [V6__performance_indexes.sql](../backend/src/main/resources/db/migration/V6__performance_indexes.sql) đánh index `(tenant_id, customer_id, created_at DESC)` cho `ledger_entries` và `(aggregate_id)`, `(tenant_id, customer_id, status)` cho `outbox_events`.
3. Gọi `CustomerEnsureService.ensureExists` theo cơ chế lazy khi `findByIdForUpdate` trả về rỗng, bỏ transaction `REQUIRES_NEW` dư thừa ở 99.9% giao dịch khách hàng đã tồn tại.
4. Bổ sung write-through cache đồng bộ trong `ApiKeyService` và `TenantService` (vô hiệu hóa/cập nhật ngay khi xoay vòng, thu hồi, đổi status hoặc đổi rule) cùng `@Async` audit ghi nhận sau khi commit cho POS success path.

---

## 5. Ma trận Nghiệm thu Yêu cầu (`FR-01`–`FR-33`, `NFR-01`–`NFR-06`)

- **Must (`FR-01`..`FR-15`, `NFR-01`..`NFR-05`)**: **Đạt 100%** (bao gồm `NFR-01` p95 `69.18ms / 66.70ms / 36.85ms`, `NFR-03` 20 luồng đồng thời, `NFR-05` live Sui Explorer digests).
- **Should (`FR-17`, `FR-18`, `FR-19`, `FR-27`, `FR-28`, `FR-29`, `FR-33`, `NFR-06`)**: **Đạt 100%** (`ShouldAndCouldExtensionsTest` + live Docker verification).
- **Could (`FR-20`, `FR-22`, `FR-23`, `FR-25`, `FR-26`, `FR-30`, `FR-31`, `FR-32`)**: **Đạt 100%** (`sdk-java`, `mcp-server`, `sdk-ts OfflineCreditQueue`, live Ledger Hash Anchor `BV6cre1JWzKZ9AVXp9u1NfGi7PDDX1yGrwnzMwbQnAqC`).
- **Won't (`FR-16`, `FR-21`, `FR-24` & Production Mainnet)**: Đặc tả kiến trúc đầy đủ tại [Future_Work_Architecture.md](./Future_Work_Architecture.md).
