# Đặc tả Cơ sở dữ liệu PostgreSQL & Sui Move Smart Contract

Tài liệu đặc tả chi tiết lược đồ dữ liệu PostgreSQL (Flyway `V1`–`V5`) đóng vai trò **Single Source of Truth (SSOT)** và mô hình **Object-Centric Sui Move Smart Contract** (`octapoint_move::octapoint`).

---

## 1. Đặc tả Lược đồ PostgreSQL (`V1` → `V5`)

Toàn bộ schema được quản lý có phiên bản bằng Flyway (`V1`–`V6`) trong [backend/src/main/resources/db/migration/](file:///d:/Y3/java/project/LAP_TRINH_JAVA/backend/src/main/resources/db/migration/).

### 1.1. Các bảng cốt lõi (`V1`–`V4`)
| Bảng | Khóa chính / Index quan trọng | Vai trò |
|---|---|---|
| `tenants` | `id (PK)` | Lưu thông tin doanh nghiệp (`PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED`), hạn mức gas, webhook và gói cước (`V5`) |
| `app_users` | `id (PK)`, `email (UNIQUE)`, FK `tenant_id` | Tài khoản Portal (`ADMIN`, `MERCHANT`, `STAFF`) với `password_hash` BCrypt |
| `api_keys` | `id (PK)`, FK `tenant_id` | Credential tích hợp; secret mã hóa **AES-256-GCM** (`encrypted_secret`, `secret_iv`) kèm `scopes` (`ISSUE,REDEEM,READ`) và `revoked_at` |
| `tenant_rules` | `id (PK)`, `tenant_id (UNIQUE FK)` | Cấu hình `earn_vnd_per_point`, `redeem_value_vnd_per_point` và quy tắc hạng thành viên/hạn điểm (`V5`) |
| `customer` | `id (PK)` = `tenantId:customerId`, `UNIQUE (tenant_id, external_customer_id)` | Số dư điểm tức thời (`balance`), `chain_seq` bắt đầu từ `-1` (tăng tuần tự mỗi tx), `lifetime_earned_points` (`V5`) |
| `ledger_entries` | `id (PK)`, FK `tenant_id` | Sổ cái điểm bất biến (`ISSUE`/`REDEEM`, `amount`, `amount_vnd`, `reference_id`, `chain_seq`, `applied_tier`, `expires_at`) |
| `idempotency_record` | `idempotency_key (PK)` = `tenant:OP:key` | Lưu `request_hash` (SHA-256) và `response_body` đảm bảo an toàn khi POS retry mạng |
| `outbox_events` | `id (PK)`, Index `(status, order_key, created_at)` | Hàng đợi settlement ghi trong **cùng transaction ACID** với `customer` & `ledger_entries` |
| `audit_logs` | `id (PK)` | Nhật ký kiểm toán thao tác nhạy cảm (`TENANT_STATUS_UPDATED`, `API_KEY_*`, `HMAC_*`, `RECONCILIATION_RUN`, ...) |

### 1.2. Mở rộng Should & Could (`V5__should_and_could_extensions.sql`)
- **`tenants`**: `gas_quota_mist` (mặc định `500_000_000`), `gas_used_mist`, `webhook_url`, `webhook_secret`, `subscription_plan` (`STARTER`).
- **`tenant_rules`**: `point_expiry_days` (`365`), `silver_threshold_points` (`500`), `gold_threshold_points` (`2000`), `silver_multiplier` (`1.20`), `gold_multiplier` (`1.50`).
- **`customer`**: `lifetime_earned_points`.
- **`ledger_entries`**: `applied_tier`, `expires_at`.
- **`outbox_events`**: `gas_used_mist`.
- **4 bảng mới**:
  1. `webhook_deliveries`: Theo dõi trạng thái gửi webhook HMAC (`PENDING`, `DELIVERED`, `FAILED`), `attempt_count`, `last_http_status`, `signature`.
  2. `reconciliation_reports`: Lưu kết quả Job đối soát `db_settled_issue_points`, `db_settled_redeem_points`, `chain_balance_value`, `delta_points`, `status` (`MATCHED`, `MATCHED_WITH_HISTORY`, `MISMATCH_ALERT`).
  3. `anomaly_alerts`: Cảnh báo giao dịch bất thường (`HIGH_SINGLE_ISSUE >= 5000`, `HIGH_FREQUENCY_CUSTOMER >= 15 tx/phút`).
  4. `ledger_anchors`: Lưu `batch_sha256_hash`, `entry_count`, `sui_tx_digest`, `walrus_blob_id` (`FR-30`).

### 1.3. Chỉ mục Tối ưu Hiệu năng (`V6__performance_indexes.sql`) & Toàn vẹn Dữ liệu
- `idx_ledger_tenant_cust_created ON ledger_entries (tenant_id, customer_id, created_at DESC)`: tăng tốc `AnomalyService` và `getBalance`/`history` theo `(tenantId, customerId)`.
- `idx_outbox_aggregate_id ON outbox_events (aggregate_id)`: tăng tốc đối soát `LedgerEntry` ↔ `OutboxEvent`.
- `idx_outbox_tenant_cust_status ON outbox_events (tenant_id, customer_id, status)`: tăng tốc kiểm tra head-of-line blocking của `OutboxSettlementWorker`.

1. **Trigger chặn sửa/xóa Sổ cái (`V2`)**:
   ```sql
   CREATE TRIGGER trg_prevent_ledger_mod
   BEFORE UPDATE OR DELETE ON ledger_entries
   FOR EACH ROW EXECUTE FUNCTION prevent_ledger_modification();
   ```
2. **Chống âm số dư đồng thời (`NFR-03`)**: `CustomerRepository.findByIdForUpdate` thực thi `SELECT ... FOR UPDATE` trên dòng `customer`, cô lập hoàn toàn các giao dịch `redeem` đồng thời.
3. **Vòng đời Outbox Worker**: `PENDING` → (`SETTLED` | `PAUSED_GAS_QUOTA` | `FAILED` sau `maxRetries = 3`), hỗ trợ `retryFailedEvent` thủ công (`FR-33`) và chặn head-of-line theo từng cặp `(tenantId, customerId)`.

---

## 2. Đặc tả Sui Move Smart Contract (`octapoint_move::octapoint`)

Source: [contracts/octapoint_move/sources/octapoint.move](file:///d:/Y3/java/project/LAP_TRINH_JAVA/contracts/octapoint_move/sources/octapoint.move).

### 2.1. Các Sui Object & Capabilities
| Định nghĩa | Loại Move | Ý nghĩa |
|---|---|---|
| `AdminCap` | `key, store` (Owned Object) | Quyền năng tối cao do ví Signer nắm giữ; mọi hàm ghi bắt buộc nhận tham chiếu `_: &AdminCap` |
| `Balance` | `key` (Shared Object) | Lưu tổng điểm phát hành/quy đổi trên Sui Testnet phục vụ settlement nhanh và đối soát tổng cung (`FR-29`) |
| `CreditAccount` | `key, store` (Object theo từng khách hàng) | Lưu `tenant_id`, `customer_id`, `balance`, `seq` nhằm đối chiếu chặt chẽ `expected_seq` |
| `SettlementEvent` | `copy, drop` (Move Event) | Sự kiện phát ra on-chain chứa `event_id`, `action` (`ISSUE`/`REDEEM`), `amount`, `seq` |

### 2.2. Các hàm Public & Quy tắc Kiểm tra On-chain
1. `init(ctx)`: Khởi tạo `AdminCap` chuyển cho deployer và chia sẻ `Balance { amount: 0 }`.
2. `issue(_: &AdminCap, balance: &mut Balance, amount: u64)`: Cộng điểm vào `Balance` shared object (hỗ trợ gọi đơn lẻ hoặc gộp nhiều lệnh `moveCall` trong **1 Programmable Transaction Block — PTB** thông qua Signer `batchIssue` `FR-26`).
3. `redeem(_: &AdminCap, balance: &mut Balance, amount: u64)`: Kiểm tra `assert!(balance.amount >= amount, E_INSUFFICIENT_BALANCE)` trước khi trừ.
4. `balance(balance: &Balance): u64`: Trả tổng số dư hiện tại (được `SignerChainClient` / `GET /internal/balance` đọc trực tiếp qua `SuiGrpcClient.getObject` cho `FR-29`).
5. `create_account`, `settle_issue`, `settle_redeem`: Kiểm tra `assert!(account.seq == expected_seq, E_SEQ_MISMATCH)` chống lặp sequence on-chain và phát `SettlementEvent`.

### 2.3. Tương tác gRPC từ Signer (`@mysten/sui/grpc`)
- [signer/src/sui.ts](file:///d:/Y3/java/project/LAP_TRINH_JAVA/signer/src/sui.ts) sử dụng `SuiGrpcClient` kết nối tới `https://fullnode.testnet.sui.io:443`.
- Các thao tác đang chạy thực tế trên Sui Testnet:
  - `issue` / `batchIssue` (gộp nhiều `moveCall` trong 1 `Transaction` PTB).
  - `redeem` (`moveCall` tới `octapoint::redeem`).
  - `readOnChainBalance` (`getObject` với `include: { json: true, content: true }` + BCS fallback).
  - `anchorHash` (`FR-30`: gửi giao dịch `pure` metadata `octapoint-ledger-anchor|v1|count|sha256` kèm zero-transfer xác nhận hash lên Sui Testnet bất biến).
