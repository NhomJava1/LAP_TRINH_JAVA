# OctaPoint: Software Requirement Specification (SRS)

| **Dự án** | OctaPoint: High-Performance Credit-as-a-Service on Sui Blockchain |
|---|---|
| **Phiên bản** | 1.0 |
| **Tiêu chuẩn áp dụng** | IEEE 830-1998 Software Requirements Specifications |
| **Căn cứ** | `docs/1.SRS/requiment-index.md`, `docs/2.UseCase/usecase-v1.md`, `docs/3.URD/user-requiment-document.md` |
| **Sơ đồ liên quan** | `docs/4.ActivityDiagram/image.png`, `docs/5.Sequence/issue_seq.png`, `docs/5.Sequence/redeem_seq.png` |
| **Trạng thái** | Dự thảo hoàn thiện, chờ nhóm và giảng viên phê duyệt |

---

## Lịch sử thay đổi

| Phiên bản | Nội dung | Căn cứ |
|---|---|---|
| 1.0 | Hoàn thiện đặc tả theo Requirement Index v0.2, URD, Use Case v1 và các sơ đồ luồng | SCRUM-11, SCRUM-12, SCRUM-13 |

---

## Mục lục

1. [Giới thiệu](#1-giới-thiệu-introduction)  
2. [Mô tả tổng thể](#2-mô-tả-tổng-thể-overall-description)  
3. [Yêu cầu giao diện ngoại vi](#3-yêu-cầu-giao-diện-ngoại-vi-external-interface-requirements)  
4. [Yêu cầu chức năng chi tiết](#4-yêu-cầu-chức-năng-chi-tiết-detailed-functional-requirements)  
5. [Yêu cầu phi chức năng](#5-yêu-cầu-phi-chức-năng-non-functional-requirements)  
6. [Mô hình dữ liệu và quy tắc nghiệp vụ](#6-mô-hình-dữ-liệu-và-quy-tắc-nghiệp-vụ-data-model-and-business-rules)  
7. [Mô tả use case chính](#7-mô-tả-use-case-chính)  
8. [Ma trận truy vết](#8-ma-trận-truy-vết-yêu-cầu-traceability-matrix)  
9. [Phạm vi ngoài MVP và hướng phát triển](#9-phạm-vi-ngoài-mvp-và-hướng-phát-triển)  
10. [Tiêu chí nghiệm thu MVP](#10-tiêu-chí-nghiệm-thu-mvp)

---

## 1. Giới thiệu (Introduction)

### 1.1. Mục đích tài liệu

Tài liệu này đặc tả các yêu cầu chức năng, yêu cầu phi chức năng, giao diện, dữ liệu, quy tắc nghiệp vụ và giới hạn triển khai của nền tảng OctaPoint. SRS là cơ sở chung cho:

- Thiết kế và phát triển Backend, Frontend, SDK và Move Smart Contract.
- Xây dựng test plan, test case và tiêu chí nghiệm thu.
- Đối chiếu phạm vi giữa nhóm phát triển, giảng viên và người kiểm thử.
- Truy vết từ yêu cầu người dùng đến chức năng hệ thống và use case.

Tài liệu đặc tả **hệ thống phải làm gì**. Chi tiết triển khai cụ thể có thể được mô tả thêm trong tài liệu kiến trúc, ADR, OpenAPI và đặc tả database/Move contract.

### 1.2. Phạm vi sản phẩm

**OctaPoint** là nền tảng Credit-as-a-Service đa tenant, cho phép doanh nghiệp tích hợp chức năng tích lũy và quy đổi điểm thông qua REST API hoặc TypeScript SDK.

Phạm vi MVP gồm:

- Đăng ký, phê duyệt và quản lý trạng thái tenant.
- Đăng nhập Portal bằng JWT và phân quyền ADMIN/MERCHANT.
- Tạo, xoay vòng, thu hồi API key; ký request bằng HMAC-SHA256.
- Cấu hình rule tích lũy và quy đổi điểm.
- Issue, redeem, tra cứu số dư và lịch sử giao dịch.
- Idempotency cho các giao dịch nghiệp vụ.
- Chống replay, rate limit và cô lập dữ liệu giữa các tenant.
- Ghi ledger append-only và transactional outbox trong PostgreSQL.
- Settlement bất đồng bộ lên Sui Testnet qua Sui Signer.
- Merchant Portal, Admin Portal và TypeScript SDK phía server.
- Audit log, giám sát cơ bản và bằng chứng giao dịch trên Sui Explorer.

Số dư nghiệp vụ tức thời được xác định bởi PostgreSQL. Dữ liệu trên Sui dùng để xác minh settlement và đối soát; Sui không thay thế PostgreSQL trong việc trả số dư API ở MVP.

### 1.3. Đối tượng đọc

| Đối tượng | Mục đích sử dụng |
|---|---|
| Nhóm Backend/Frontend/Smart Contract | Phát triển đúng yêu cầu và ranh giới hệ thống |
| QA/Tester | Xây dựng test case, integration test và nghiệm thu |
| Giảng viên/hội đồng | Đánh giá phạm vi, thiết kế và mức độ hoàn thành |
| Người tích hợp Merchant System | Sử dụng API và SDK đúng giao thức |
| System Admin | Vận hành, phê duyệt tenant và theo dõi settlement |

### 1.4. Thuật ngữ và từ viết tắt

| Thuật ngữ | Định nghĩa |
|---|---|
| **Tenant** | Doanh nghiệp sử dụng nền tảng OctaPoint; dữ liệu được cô lập với tenant khác. |
| **Merchant** | Người đại diện hoặc người dùng Portal của tenant. |
| **Merchant System** | POS/e-commerce backend của merchant, gọi API qua SDK. |
| **Issue** | Cấp/cộng điểm cho khách hàng theo giá trị đơn hàng. |
| **Redeem** | Quy đổi/trừ điểm khi khách hàng sử dụng điểm. |
| **Ledger** | Sổ cái giao dịch điểm, chỉ ghi thêm (append-only). |
| **Settlement** | Quá trình xác nhận giao dịch nghiệp vụ lên Sui. |
| **Outbox** | Hàng đợi sự kiện được ghi trong cùng transaction với giao dịch nghiệp vụ. |
| **SSOT** | Single Source of Truth; PostgreSQL là nguồn dữ liệu nghiệp vụ có thẩm quyền. |
| **Idempotency-Key** | Khóa giúp request gửi lại không tạo thêm giao dịch. |
| **HMAC** | Hash-based Message Authentication Code dùng để xác thực request. |
| **Replay** | Gửi lại nguyên request đã ký và nonce đã sử dụng. |
| **Nonce** | Giá trị dùng một lần để chống replay. |
| **PTB** | Programmable Transaction Block của Sui. |
| **Ghost transaction** | Request gửi lên chain bị timeout nhưng giao dịch thực tế đã thành công. |
| **`chain_seq`** | Số thứ tự tuần tự của giao dịch theo từng cặp tenant/customer. |
| **`settlementStatus`** | Trạng thái settlement: `PENDING`, `SETTLED`, `FAILED`. |
| **`settlementMode`** | Chế độ settlement: `LIVE` hoặc `MOCK`, độc lập với `settlementStatus`. |
| **MVP** | Minimum Viable Product; phạm vi bắt buộc để nghiệm thu. |
| **RBAC** | Role-Based Access Control; phân quyền theo vai trò. |

### 1.5. Tài liệu tham khảo

1. `docs/1.SRS/requiment-index.md` — mã FR/NFR, ưu tiên MoSCoW, tiêu chí nghiệm thu và kịch bản demo.
2. `docs/2.UseCase/usecase-v1.md` — actor, use case và luồng chính.
3. `docs/3.URD/user-requiment-document.md` — nhu cầu người dùng và quy tắc nghiệp vụ.
4. `docs/4.ActivityDiagram/image.png` — luồng xử lý API issue và settlement.
5. `docs/5.Sequence/issue_seq.png` — sequence issue.
6. `docs/5.Sequence/redeem_seq.png` — sequence redeem.
7. IEEE Std 830-1998.
8. Tài liệu Sui, Sui Move và `@mysten/sui` SDK.

---

## 2. Mô tả tổng thể (Overall Description)

### 2.1. Bối cảnh và mục tiêu sản phẩm

Các hệ thống điểm thưởng tập trung thường gặp rủi ro spam API, giả mạo request, phát hành điểm trái phép, sai lệch số dư và khó đối soát. OctaPoint cung cấp một lớp dịch vụ dùng chung để merchant không phải tự xây engine tín dụng và hạ tầng blockchain.

Mục tiêu chính:

| Mã | Mục tiêu | Chỉ số/đầu ra |
|---|---|---|
| G1 | Merchant cộng/trừ điểm an toàn | Issue/redeem qua API hoặc SDK chạy đúng |
| G2 | Chống gian lận cơ bản | HMAC, timestamp, nonce, rate limit, RBAC |
| G3 | Đối soát được giao dịch | Digest và event trên Sui Testnet |
| G4 | Đáp ứng nhanh | p95 API off-chain dưới 100 ms theo NFR-01 |
| G5 | Dễ tích hợp | TypeScript SDK, tài liệu API và hướng dẫn sử dụng |

### 2.2. Kiến trúc logic tổng quan

OctaPoint dùng kiến trúc modular monolith cho core và các dịch vụ ngoại vi cho Portal, SDK, Signer:

1. **Client tier:** Merchant Portal, Admin Portal, Merchant POS/e-commerce và TypeScript SDK.
2. **API Gateway/Security tier:** Spring Security Filter Chain, kiểm tra HMAC, timestamp, nonce, scope và rate limit.
3. **Application Core:** Spring Boot xử lý tenant, rule, customer, issue, redeem, idempotency và transaction nghiệp vụ.
4. **Persistence tier:** PostgreSQL lưu tenant, user, rule, balance, ledger, idempotency, outbox và audit log.
5. **Cache/Security state:** Redis lưu cache rule/tenant, nonce đã dùng và trạng thái rate limit. Redis không phải nguồn dữ liệu số dư.
6. **Settlement tier:** Outbox Worker lấy sự kiện theo thứ tự, gọi Sui Signer qua mạng nội bộ.
7. **Blockchain tier:** Sui Signer ký và gửi PTB lên Sui Testnet; Move contract kiểm tra quyền và `seq`.

Luồng nghiệp vụ tổng quát:

```text
Merchant System/Portal
        |
        v
API Gateway -> Spring Boot Core -> PostgreSQL
                    |              |
                    |              +--> Ledger + Outbox
                    v
                  Redis
                                   |
                                   v
                         Outbox Worker -> Sui Signer -> Sui Testnet
```

### 2.3. Actor và lớp người dùng

| Actor/Vai trò | Giao diện | Quyền và trách nhiệm | Mức |
|---|---|---|---|
| **Merchant** | Merchant Portal | Đăng ký, xem trạng thái tenant, quản lý API key, cấu hình rule, xem số dư/lịch sử | Must |
| **System Admin** | Admin Portal | Đăng nhập, duyệt/từ chối/khóa tenant, xem dashboard, giao dịch lỗi và audit log | Must |
| **Merchant System** | REST API + TypeScript SDK | Issue, redeem, balance, history bằng API key và HMAC | Must |
| **Sui Blockchain** | Qua Sui Signer | Nhận giao dịch settlement và phát event | Must |
| **AI Agent** | MCP Server | Truy vấn balance/history/rule trong phạm vi tenant | Should |

End-user/khách tích điểm không tương tác trực tiếp trong MVP; họ được đại diện bởi `customerId` do Merchant System cung cấp. Developer cũng không phải actor riêng; hành vi của developer thể hiện qua Merchant System và SDK.

### 2.4. Phân loại chức năng theo MoSCoW

| Mức | Phạm vi |
|---|---|
| **Must** | FR-01 đến FR-15; NFR-01 đến NFR-05; các chức năng core, Portal, SDK và settlement |
| **Should** | FR-17, FR-18, FR-19, FR-27, FR-28, FR-29; MCP, rule nâng cao, analytics, gas quota, webhook, đối soát |
| **Could** | FR-20, FR-22, FR-23, FR-25, FR-26, FR-30, FR-31, FR-32, FR-33 |
| **Won't** | FR-16, FR-21, FR-24; Sui Mainnet, Stripe thật, production uptime 99,9%, mTLS thực tế và các mục được nêu tại mục 9 |

### 2.5. Môi trường vận hành

| Thành phần | Yêu cầu |
|---|---|
| Backend | Java 21 LTS, Spring Boot 3.3+, Spring Security |
| Database | PostgreSQL 16+ |
| Cache | Redis 7+ |
| Signer | Node.js 20+ LTS, TypeScript 5+, `@mysten/sui` |
| Blockchain | Sui Testnet; RPC endpoint cấu hình qua biến môi trường |
| Frontend | TypeScript; Merchant Portal và Admin Portal |
| Đóng gói | Docker Compose cho môi trường local/demo |
| CI/CD | GitHub Actions: build, static check, unit test, integration test |

### 2.6. Ràng buộc thiết kế và triển khai

1. PostgreSQL là nguồn dữ liệu chính cho số dư, ledger và trạng thái nghiệp vụ.
2. Issue/redeem phải cập nhật balance, ledger và outbox trong cùng một database transaction.
3. `ledger_entry` append-only; không được `UPDATE` hoặc `DELETE` giao dịch đã ghi.
4. API secret không được lưu plain text; phải mã hóa khi lưu trữ và không xuất hiện trong log.
5. Private key của Signer không được commit vào Git hoặc nhúng trong source code.
6. Signer không mở endpoint public; chỉ nhận request nội bộ đã xác thực.
7. REST là giao diện API bắt buộc của MVP; gRPC không bắt buộc.
8. SDK TypeScript chỉ chạy phía server/backend; không nhúng secret vào frontend/browser.
9. Không sử dụng sponsored transaction hai chữ ký trong MVP; Signer tự trả gas.
10. Không tuyên bố outbox bảo đảm exactly-once execution trên blockchain nếu chưa có kiểm tra trạng thái on-chain.

### 2.7. Giả định và phụ thuộc

- Merchant có hệ thống POS/e-commerce và cung cấp `customerId`, `orderId` hoặc `businessTransactionId`.
- Sui Testnet và RPC có thể tạm thời chậm hoặc không khả dụng; hệ thống phải có `MockChainClient` cho kiểm thử/dự phòng.
- Rule tối thiểu gồm `earnVndPerPoint` và `redeemValueVndPerPoint`, đều là số nguyên dương.
- `settlementStatus` và `settlementMode` là hai thuộc tính độc lập.
- Transaction digest chỉ được lưu sau khi xác minh settlement thành công.
- Với kết quả chain không rõ ràng, worker phải kiểm tra on-chain trước khi retry nếu có đủ thông tin.

---

## 3. Yêu cầu giao diện ngoại vi (External Interface Requirements)

### 3.1. Giao diện người dùng

#### 3.1.1. Merchant Portal

Portal phải cung cấp:

- Đăng ký tenant và hiển thị trạng thái `PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED`.
- Đăng nhập và làm mới phiên bằng JWT/Refresh Token.
- Tạo, xoay vòng và thu hồi API key.
- Hiển thị API secret đúng một lần sau khi tạo/rotate; không hiển thị lại sau đó.
- Cấu hình `earnVndPerPoint` và `redeemValueVndPerPoint`.
- Tra cứu số dư và lịch sử giao dịch theo `customerId`.
- Hiển thị `settlementStatus`, `settlementMode`, `eventId` và `transactionDigest` nếu có.

#### 3.1.2. Admin Portal

Portal phải cung cấp:

- Danh sách tenant và thao tác Approve/Reject/Lock.
- Dashboard số tenant, số giao dịch, số lượng outbox theo trạng thái.
- Danh sách giao dịch `FAILED` và nguyên nhân thất bại.
- Audit log gồm actor, action, đối tượng, thời điểm, kết quả và correlation ID.
- Thông tin health/metric cơ bản của hệ thống.

Retry thủ công giao dịch `FAILED` là FR-33, thuộc Could; MVP bắt buộc hiển thị giao dịch lỗi theo FR-13 nhưng không bắt buộc nút retry thủ công.

### 3.2. Giao diện API REST

Các endpoint logic tối thiểu:

| Nhóm | Endpoint ví dụ | Xác thực |
|---|---|---|
| Portal | `POST /api/portal/v1/tenants`, `POST /api/portal/v1/auth/login` | Public cho đăng ký; JWT cho các thao tác còn lại |
| Admin | `PATCH /api/admin/v1/tenants/{tenantId}/status` | JWT + role ADMIN |
| API key | `POST /api/portal/v1/api-keys`, `POST /api/portal/v1/api-keys/{keyId}/rotate`, `DELETE /api/portal/v1/api-keys/{keyId}` | JWT + tenant ownership |
| Rule | `GET/PUT /api/portal/v1/rules` | JWT + tenant ownership |
| Credit | `POST /api/merchant/v1/issue`, `POST /api/merchant/v1/redeem` | API key + HMAC + scope |
| Query | `GET /api/merchant/v1/balance/{customerId}`, `GET /api/merchant/v1/transactions` | API key + HMAC + scope |
| Admin query | `GET /api/admin/v1/dashboard`, `GET /api/admin/v1/settlements/failed`, `GET /api/admin/v1/audit-logs` | JWT + role ADMIN |

#### 3.2.1. Request issue

```json
{
  "customerId": "C1",
  "orderId": "ORDER-001",
  "amountVnd": 100000
}
```

#### 3.2.2. Request redeem

```json
{
  "customerId": "C1",
  "orderId": "ORDER-002",
  "pointsToRedeem": 30
}
```

#### 3.2.3. Response nghiệp vụ thành công

```json
{
  "eventId": "ledger-entry-uuid",
  "customerId": "C1",
  "points": 100,
  "balanceAfter": 100,
  "settlementStatus": "PENDING",
  "settlementMode": "LIVE",
  "transactionDigest": null
}
```

#### 3.2.4. HTTP status tối thiểu

| Status | Trường hợp |
|---|---|
| `200 OK` | Truy vấn, redeem thành công hoặc trả kết quả cũ theo idempotency |
| `201 Created` | Đăng ký tenant, tạo giao dịch issue mới |
| `400 Bad Request` | DTO hoặc dữ liệu đầu vào không hợp lệ |
| `401 Unauthorized` | Sai HMAC, timestamp, nonce, JWT hoặc API key không hợp lệ |
| `403 Forbidden` | Sai role/scope, tenant chưa APPROVED hoặc truy cập tenant khác |
| `409 Conflict` | Idempotency key trùng nhưng payload khác; xung đột trạng thái |
| `422 Unprocessable Entity` | Redeem vượt số dư (`INSUFFICIENT_BALANCE`) |
| `429 Too Many Requests` | Vượt rate limit |

### 3.3. Giao thức xác thực API và HMAC

Mọi request đến `/api/merchant/v1/**` phải có:

- `X-OP-Key`: public key ID.
- `X-OP-Timestamp`: Unix timestamp theo giây.
- `X-OP-Nonce`: UUID v4 dùng một lần.
- `X-OP-Signature`: chữ ký HMAC-SHA256.
- `Idempotency-Key`: bắt buộc với issue/redeem.

Timestamp mặc định chỉ lệch tối đa 300 giây. Nonce được lưu Redis trong khoảng hiệu lực và không được dùng lại.

Chuỗi canonical:

```text
METHOD + "\n" + PATH + "\n" + NORMALIZED_QUERY + "\n"
+ TIMESTAMP + "\n" + NONCE + "\n" + SHA256(BODY)
```

Quy tắc:

1. `METHOD` viết hoa.
2. `PATH` là path request chuẩn hóa, không gồm host.
3. `NORMALIZED_QUERY` sắp xếp tham số theo tên và mã hóa theo quy ước API; để rỗng nếu không có query.
4. `BODY` là chuỗi JSON chính xác được gửi; body rỗng dùng SHA-256 của chuỗi rỗng.
5. HMAC được tính bằng API secret tương ứng với `X-OP-Key`.
6. So sánh chữ ký bằng constant-time comparison.

Pipeline Gateway:

1. Kiểm tra rate limit.
2. Đọc key và kiểm tra trạng thái `ACTIVE`.
3. Kiểm tra timestamp.
4. Tính và xác minh HMAC.
5. Lưu nonce bằng thao tác nguyên tử `SETNX` kèm TTL.
6. Kiểm tra scope và tenant status.
7. Chuyển request vào application core.

Chống replay và idempotency là hai cơ chế riêng biệt. Request gửi lại với cùng Idempotency-Key phải có timestamp, nonce mới và chữ ký hợp lệ. Gửi lại nguyên request đã ký phải bị từ chối do nonce đã dùng.

### 3.4. Giao diện phần mềm

| Hệ thống | Giao diện | Yêu cầu |
|---|---|---|
| PostgreSQL | JDBC/HikariCP, Flyway | ACID transaction; constraint; index; migration có version |
| Redis | Spring Data Redis/Lettuce | Cache, nonce, rate limit; không lưu SSOT số dư |
| Sui Signer | HTTP nội bộ | Request có service token; không public Internet |
| Sui | JSON-RPC/gRPC qua SDK | Gửi PTB, đọc object/event/transaction để xác minh |
| TypeScript SDK | REST client | Tự ký HMAC, tạo nonce và hỗ trợ idempotency |
| MockChainClient | Interface nội bộ | Mô phỏng thành công, lỗi, timeout và kiểm tra retry |

### 3.5. Giao diện truyền thông và bảo mật

- Client đến API dùng HTTPS trong môi trường triển khai có TLS.
- Signer chỉ giao tiếp qua Docker internal network hoặc mạng riêng tương đương.
- Request nội bộ Backend-Signer phải có `X-Service-Token`; token không ghi vào log.
- Mật khẩu Portal lưu bằng hash mạnh, ví dụ BCrypt/Argon2; không thể khôi phục ngược.
- API secret mã hóa bằng AES-256-GCM với khóa mã hóa lấy từ secret manager/biến môi trường bảo mật.
- Private key blockchain chỉ được cấp cho Signer qua cơ chế cấu hình an toàn.

---

## 4. Yêu cầu chức năng chi tiết (Detailed Functional Requirements)

### 4.1. FR-01 — Đăng ký tenant

**Mô tả:** Merchant tự đăng ký tenant và tài khoản đại diện.

**Đầu vào:** Tên doanh nghiệp, email, mật khẩu và các trường bắt buộc theo API.

**Xử lý:**

1. Validate dữ liệu.
2. Kiểm tra email chưa tồn tại.
3. Hash mật khẩu.
4. Tạo tenant với trạng thái `PENDING`.
5. Tạo user role `MERCHANT`/`OWNER` thuộc tenant.
6. Ghi audit log cho thao tác đăng ký nếu cần truy vết.

**Đầu ra:** `201 Created`; tenant chưa được issue/redeem khi chưa được duyệt.

**Lỗi:** Dữ liệu sai `400`; email đã tồn tại `409`.

### 4.2. FR-02 — Admin duyệt, từ chối hoặc khóa tenant

1. Chỉ `ADMIN` được thay đổi trạng thái tenant.
2. Trạng thái hợp lệ: `PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED`.
3. Tenant `APPROVED` mới được gọi issue/redeem.
4. Tenant `REJECTED` hoặc `SUSPENDED` bị từ chối giao dịch với `403`.
5. Thay đổi trạng thái phải ghi audit log.
6. Cache trạng thái tenant phải được xóa hoặc cập nhật sau khi transaction thành công.
7. Chuyển trạng thái không hợp lệ trả `409`.

### 4.3. FR-03 — Quản lý vòng đời API key

Hệ thống phải hỗ trợ:

- Tạo cặp `keyId` công khai và `secretKey` ngẫu nhiên bằng bộ sinh mật mã an toàn.
- Hiển thị secret đúng một lần trong response tạo/rotate.
- Mã hóa secret trước khi lưu PostgreSQL.
- Thu hồi key, sau đó key không được xác thực request.
- Xoay vòng bằng cách tạo key mới và thu hồi key cũ; MVP không bắt buộc grace period.
- Gán scope tối thiểu: `ISSUE`, `REDEEM`, `READ`.
- Không ghi secret vào audit log, application log, response sau lần hiển thị đầu tiên hoặc Git.

### 4.4. FR-04 — Đăng nhập và RBAC Portal

1. Merchant/Admin đăng nhập bằng email và mật khẩu.
2. Access JWT có TTL mặc định 15 phút.
3. Refresh Token có TTL mặc định 7 ngày và phải được quản lý an toàn.
4. JWT chứa tối thiểu `userId`, `tenantId` và `role`.
5. Vai trò MVP: `ADMIN`, `MERCHANT`.
6. Merchant chỉ truy cập tài nguyên có `tenantId` của mình.
7. Admin được truy cập chức năng quản trị theo role.
8. Tài khoản/tenant bị khóa không được đăng nhập hoặc gọi chức năng bị hạn chế.

### 4.5. FR-05 — Issue điểm

**Đầu vào bắt buộc:** `customerId`, `orderId` hoặc `businessTransactionId`, `amountVnd`, `Idempotency-Key`.

**Điều kiện:** Tenant `APPROVED`, API key `ACTIVE`, scope `ISSUE`, HMAC hợp lệ.

**Xử lý trong một database transaction:**

1. Validate `amountVnd` là số nguyên dương.
2. Đọc rule hiện tại của tenant.
3. Tính `points = floor(amountVnd / earnVndPerPoint)`.
4. Tạo customer/balance nếu chưa tồn tại.
5. Tăng balance và cấp `chain_seq` nguyên tử.
6. Insert một `ledger_entry` loại `ISSUE`.
7. Insert một `outbox_event` loại `MINT`, trạng thái `PENDING`.
8. Commit toàn bộ hoặc rollback toàn bộ.

**Idempotency:**

- Cùng tenant + cùng `Idempotency-Key` + cùng request hash: trả kết quả cũ, không cộng thêm điểm.
- Cùng tenant + cùng key nhưng request hash khác: trả `409 Conflict`.
- Key của tenant này không có tác dụng idempotency cho tenant khác.

**Đầu ra:** `201 Created`, `settlementStatus = PENDING` nếu là giao dịch mới; phản hồi off-chain mục tiêu không quá 100 ms.

### 4.6. FR-06 — Redeem điểm

**Đầu vào bắt buộc:** `customerId`, `orderId`, `pointsToRedeem`, `Idempotency-Key`.

**Xử lý:**

1. Validate `pointsToRedeem` là số nguyên dương.
2. Kiểm tra tenant, API key, HMAC và scope `REDEEM`.
3. Thực hiện kiểm tra và trừ điểm nguyên tử trong PostgreSQL:

```sql
UPDATE customer_balance
SET amount = amount - :points,
    next_chain_seq = next_chain_seq + 1,
    updated_at = NOW()
WHERE tenant_id = :tenant_id
  AND customer_id = :customer_id
  AND amount >= :points
RETURNING amount, next_chain_seq - 1 AS chain_seq;
```

4. Nếu không có dòng được cập nhật, rollback và trả `422 INSUFFICIENT_BALANCE`.
5. Nếu thành công, insert ledger loại `REDEEM` và outbox loại `REDEEM` trong cùng transaction.
6. Không ghi ledger giao dịch thành công và không ghi outbox nếu số dư không đủ.

**Đầu ra:** `200 OK`, số dư mới và `settlementStatus = PENDING`.

### 4.7. FR-07 — Tra cứu số dư và lịch sử

1. Merchant System được tra cứu balance/history qua API key scope `READ`.
2. Merchant được xem dữ liệu tenant của mình qua Portal.
3. Kết quả phải lọc theo `tenantId`; không tin `tenantId` do client tự gửi.
4. Balance đọc từ PostgreSQL; Redis chỉ được dùng làm cache không có thẩm quyền.
5. History hỗ trợ phân trang và lọc theo customer, thời gian, loại giao dịch, trạng thái settlement.
6. Mỗi giao dịch trả tối thiểu `eventId`, `customerId`, loại, points, `balanceAfter`, thời điểm, `settlementStatus`, `settlementMode` và `transactionDigest` nếu có.

### 4.8. FR-08 — Cấu hình rule earn/redeem

1. Mỗi tenant có rule riêng.
2. Rule tối thiểu:
   - `earnVndPerPoint`: số VND cần chi tiêu cho một điểm.
   - `redeemValueVndPerPoint`: giá trị VND của một điểm khi quy đổi.
3. Hai giá trị phải là số nguyên dương.
4. Rule mới chỉ áp dụng cho giao dịch phát sinh sau thời điểm cập nhật.
5. Giá trị demo mặc định: `1.000 VND = 1 điểm`, `1 điểm = 10 VND`.
6. Cập nhật rule phải được audit log và làm mất hiệu lực cache cũ.
7. Merchant chỉ được cập nhật rule của tenant mình.

### 4.9. FR-09 — Settlement bất đồng bộ bằng transactional outbox

1. Issue/redeem thành công phải tạo outbox event trong cùng transaction với ledger.
2. Worker chạy theo chu kỳ cấu hình 5–10 giây khi có sự kiện chờ.
3. Worker lấy event `PENDING` đến hạn retry bằng `FOR UPDATE SKIP LOCKED`.
4. Worker bảo đảm thứ tự `chain_seq` theo từng cặp tenant/customer; không để event sau vượt event trước đang lỗi.
5. Worker chuyển event sang `PROCESSING` và commit trước khi gọi Signer.
6. Worker gom event thành batch nhưng phải giữ thứ tự logic theo customer.
7. Request gửi Signer có `eventId`, tenant/customer, loại, points, `chain_seq`, payload và service token.
8. Sau khi xác minh thành công trên Sui, worker lưu digest và chuyển event sang `SETTLED`.
9. Lỗi hoặc timeout phải tăng attempts, tính exponential backoff và chuyển lại `PENDING` nếu còn retry.
10. Sau tối đa 5 lần retry, chuyển `FAILED`, lưu nguyên nhân và hiển thị cho Admin.
11. Kết quả timeout/không rõ phải được kiểm tra on-chain trước khi gửi lại.
12. `settlementMode = MOCK` chỉ dùng cho MockChainClient; không được gán digest cũ cho giao dịch mock mới.

### 4.10. FR-10 — Smart contract Sui Move

MVP dùng một package Move và object cấu hình/tài khoản theo tenant, không triển khai module riêng cho từng tenant.

Mỗi cặp `(tenant, customer)` có một `CreditAccount` on-chain với tối thiểu:

- `tenant_id` hoặc thông tin định danh tenant.
- `customer_id` hoặc thông tin định danh customer.
- `balance`.
- `seq`, bắt đầu từ 0.

Contract phải:

1. Kiểm tra quyền gọi của Signer/đối tượng quản trị.
2. Nhận thao tác mint/redeem kèm `eventId`, `chain_seq` và dữ liệu giao dịch.
3. Kiểm tra `chain_seq` khớp `CreditAccount.seq`.
4. Từ chối sequence sai bằng lỗi tương đương `EINVALID_SEQ`.
5. Cập nhật balance và tăng `seq` trong cùng transaction Move.
6. Phát event `Minted` hoặc `Redeemed` chứa eventId và thông tin cần đối soát.
7. Dùng capability quản trị tương đương `AdminCap` cho thao tác quản trị.
8. Cho phép Signer đọc object/event/transaction để worker xác minh settlement.

Option A với `seq` là thiết kế MVP. Nếu kiểm chứng Sui/Move cho thấy không khả thi, nhóm phải ghi ADR thay thế và không được tuyên bố exactly-once on-chain chỉ dựa vào outbox.

### 4.11. FR-11 — TypeScript SDK

SDK server-side phải cung cấp:

- `issue()`.
- `redeem()`.
- `getBalance()`.
- `getHistory()`.

SDK tự động:

1. Tạo timestamp và UUID nonce.
2. Tính SHA-256 body.
3. Chuẩn hóa canonical string.
4. Tạo HMAC-SHA256 bằng secret.
5. Gắn `Idempotency-Key` cho issue/redeem nếu caller chưa cung cấp.
6. Trả về lỗi HTTP/API theo dạng dễ xử lý.

SDK không được khuyến khích hoặc hỗ trợ nhúng secret vào browser/frontend. SDK và Backend phải dùng chung HMAC test vector.

### 4.12. FR-12 — Merchant Portal

Portal phải hỗ trợ các chức năng tại mục 3.1.1, tối thiểu:

- Đăng ký/đăng nhập và xem trạng thái tenant.
- Quản lý API key.
- Cấu hình rule.
- Xem balance/history và trạng thái settlement.
- Chỉ hiển thị dữ liệu của tenant hiện tại.

### 4.13. FR-13 — Admin Portal

Admin Portal phải hỗ trợ:

- Duyệt, từ chối, khóa tenant.
- Dashboard tổng quan.
- Xem settlement `PENDING`, `SETTLED`, `FAILED`.
- Xem danh sách FAILED kèm nguyên nhân.
- Xem audit log cơ bản.

Nút retry thủ công là FR-33, không bắt buộc trong MVP.

### 4.14. FR-14 — Security Gateway

Gateway phải:

- Kiểm tra API key tồn tại, còn `ACTIVE` và thuộc tenant hợp lệ.
- Kiểm tra rate limit trước khi xử lý nghiệp vụ.
- Kiểm tra timestamp, chữ ký HMAC và nonce.
- Kiểm tra scope (`ISSUE`, `REDEEM`, `READ`).
- Trả `401` cho chữ ký sai, timestamp hết hạn, nonce trùng hoặc credential không hợp lệ.
- Trả `403` cho sai scope, tenant chưa duyệt hoặc truy cập trái tenant.
- Trả `429` khi vượt rate limit.
- Không ghi secret, private key, JWT hoặc toàn bộ body nhạy cảm vào log.

### 4.15. FR-15 — Audit log

Audit log phải ghi tối thiểu:

- Actor/user/service thực hiện.
- Tenant liên quan.
- Action.
- Loại và ID đối tượng.
- Thời điểm.
- Kết quả thành công/thất bại.
- Error code nếu có.
- Correlation/request ID.

Các thao tác bắt buộc audit gồm: thay đổi trạng thái tenant, tạo/rotate/revoke API key, cập nhật rule và các thao tác quản trị nhạy cảm. Secret, private key, JWT và credential không được ghi vào log.

### 4.16. Các yêu cầu Should/Could có liên quan

Các yêu cầu sau được ghi nhận để bảo đảm không bỏ sót mã yêu cầu, nhưng không làm điều kiện bắt buộc của MVP:

| Mã | Mô tả |
|---|---|
| FR-17 | MCP Server với công cụ truy vấn balance/history/rule; giới hạn theo tenant; không issue/redeem |
| FR-18 | Rule nâng cao: hạn dùng, tier, multiplier |
| FR-19 | Analytics cơ bản bằng PostgreSQL |
| FR-27 | Hạn mức gas theo tenant |
| FR-28 | Webhook khi settlement `SETTLED`, ký HMAC và retry |
| FR-29 | Job đối soát PostgreSQL với `CreditAccount` on-chain |
| FR-20 | Gói dịch vụ và hóa đơn giả lập |
| FR-22 | Phát hiện bất thường theo rule đơn giản |
| FR-23 | Cập nhật realtime bằng SSE |
| FR-25 | Prototype SDK Java |
| FR-26 | Offline-first/batch PTB nâng cao |
| FR-30 | Anchor hash lô ledger lên Sui |
| FR-31 | Vai trò STAFF |
| FR-32 | MCP tool ghi, bắt buộc xác nhận người dùng |
| FR-33 | Admin retry thủ công settlement FAILED |

---

## 5. Yêu cầu phi chức năng (Non-Functional Requirements)

### 5.1. NFR-01 — Hiệu năng

1. API off-chain `issue`, `redeem`, `balance` đạt p95 dưới 100 ms.
2. Đo với 20 virtual users và tối thiểu 1.000 request cho mỗi endpoint sau warm-up.
3. Không tính thời gian chờ settlement Sui.
4. Báo cáo phải ghi rõ CPU, RAM, OS, Docker Compose, database và cấu hình test.
5. Đo lần đầu từ Sprint 4 để có thời gian tối ưu.

### 5.2. NFR-02 — Bảo mật

1. Không commit API secret, private key hoặc `.env` thật.
2. API secret mã hóa khi lưu trữ.
3. Mật khẩu lưu dưới dạng hash một chiều.
4. JWT có RBAC và kiểm tra tenant ownership ở server.
5. HMAC, timestamp, nonce và rate limit được kiểm thử riêng.
6. Secret scanning phải không phát hiện credential thật.
7. Không trả thông tin nhạy cảm trong error message hoặc log.
8. Signer không mở public endpoint.

### 5.3. NFR-03 — Độ tin cậy, đồng thời và toàn vẹn dữ liệu

1. Balance không bao giờ âm.
2. Issue/redeem phải có tính nguyên tử giữa balance, ledger và outbox.
3. Với balance ban đầu 100 điểm và 20 request đồng thời, mỗi request redeem 10 điểm:
   - Đúng 10 request thành công.
   - Đúng 10 request trả `422`.
   - Balance cuối bằng 0.
   - Tổng ledger khớp balance.
   - Không deadlock và không mất giao dịch.
4. Ledger không được sửa/xóa.
5. Worker phải retry lỗi với backoff và bảo toàn thứ tự theo customer.
6. Timeout chain phải được đối chiếu trước retry để hạn chế duplicate.

### 5.4. NFR-04 — CI/CD và chất lượng mã nguồn

GitHub Actions phải thực hiện tối thiểu:

- Build Backend/Frontend/Signer theo cấu hình dự án.
- Static check/format/checkstyle phù hợp module.
- Unit test.
- Integration test với PostgreSQL/Redis Testcontainers hoặc dịch vụ tương đương.
- Test HMAC, idempotency, RBAC, tenant isolation, concurrency và settlement retry.

PR chỉ được merge khi các kiểm thử bắt buộc thành công. Không bỏ qua hook hoặc test để làm CI xanh giả tạo.

### 5.5. NFR-05 — Khả năng kiểm chứng settlement

1. Mỗi giao dịch `SETTLED` phải có `transactionDigest`.
2. Digest phải truy vấn được trên Sui Explorer Testnet.
3. Nhóm phải lưu bằng chứng đầu tiên ngay khi settlement chạy được, gồm digest và ảnh Explorer.
4. Báo cáo tuần 7 cập nhật bằng chứng mới nhất.
5. Nếu Testnet lỗi trong demo, dùng bằng chứng đã lưu/video và ghi rõ trạng thái demo trực tiếp.
6. Giao dịch `MOCK` phải hiển thị rõ `settlementMode = MOCK` và không dùng digest cũ.

### 5.6. NFR-06 — Giám sát cơ bản

- Spring Boot Actuator cung cấp health endpoint.
- Có metric tối thiểu cho request, latency, lỗi API, queue/outbox và settlement.
- Admin có thể xem trạng thái cơ bản của hệ thống.
- Không cam kết uptime production 99,9% trong MVP.

### 5.7. Khả năng bảo trì và triển khai

- Migration database có version và chạy lặp an toàn.
- Cấu hình môi trường tách khỏi source code.
- Docker Compose khởi động được các thành phần local theo README.
- Tài liệu API/OpenAPI và hướng dẫn cài đặt phải đồng bộ với implementation.
- Lỗi phải có mã lỗi ổn định để SDK và client xử lý.

---

## 6. Mô hình dữ liệu và quy tắc nghiệp vụ (Data Model and Business Rules)

### 6.1. Thực thể chính

| Thực thể | Vai trò |
|---|---|
| `tenant` | Doanh nghiệp và trạng thái hoạt động |
| `user` | Tài khoản Portal, role và tenant ownership |
| `api_key` | Credential tích hợp, secret mã hóa, scope và trạng thái |
| `tenant_rule` | Rule earn/redeem theo tenant |
| `customer_balance` | Số dư và sequence off-chain theo tenant/customer |
| `ledger_entry` | Giao dịch điểm append-only |
| `idempotency_record` | Request hash và kết quả trả về để xử lý request lặp |
| `outbox_event` | Sự kiện cần settlement |
| `audit_log` | Nhật ký thao tác nhạy cảm |

### 6.2. Quan hệ và khóa dữ liệu

- Một `tenant` có nhiều `user`, `api_key`, `customer_balance`, `ledger_entry`, `outbox_event`, `audit_log` và một hoặc nhiều phiên bản `tenant_rule`.
- `customer_balance` định danh duy nhất bằng `(tenant_id, customer_id)`; không được dùng `customer_id` đơn độc trong hệ thống đa tenant.
- `ledger_entry` có khóa duy nhất `(tenant_id, idempotency_key)`.
- `outbox_event.id` là `eventId` dùng truy vết xuyên suốt DB, worker, Signer và Move event.
- `chain_seq` bắt đầu từ 0 và tăng tuần tự theo từng `(tenant_id, customer_id)`.
- Các trạng thái settlement hợp lệ: `PENDING`, `PROCESSING`, `SETTLED`, `FAILED`.
- `settlementMode` hợp lệ: `LIVE`, `MOCK`.

### 6.3. Lược đồ PostgreSQL tối thiểu

```sql
CREATE TABLE tenant (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED'))
);

CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    tenant_id UUID REFERENCES tenant(id),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (role IN ('ADMIN', 'MERCHANT'))
);

CREATE TABLE api_key (
    key_id VARCHAR(64) PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    encrypted_secret TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    scopes VARCHAR(100) NOT NULL DEFAULT 'ISSUE,REDEEM,READ',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ,
    CHECK (status IN ('ACTIVE', 'REVOKED'))
);

CREATE TABLE tenant_rule (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    earn_vnd_per_point BIGINT NOT NULL CHECK (earn_vnd_per_point > 0),
    redeem_value_vnd_per_point BIGINT NOT NULL CHECK (redeem_value_vnd_per_point > 0),
    effective_from TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE customer_balance (
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    customer_id VARCHAR(128) NOT NULL,
    amount BIGINT NOT NULL DEFAULT 0 CHECK (amount >= 0),
    next_chain_seq BIGINT NOT NULL DEFAULT 0 CHECK (next_chain_seq >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, customer_id)
);

CREATE TABLE ledger_entry (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    customer_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    entry_type VARCHAR(20) NOT NULL,
    points BIGINT NOT NULL CHECK (points > 0),
    balance_after BIGINT NOT NULL CHECK (balance_after >= 0),
    chain_seq BIGINT NOT NULL CHECK (chain_seq >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, idempotency_key),
    CHECK (entry_type IN ('ISSUE', 'REDEEM'))
);

CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    customer_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    chain_seq BIGINT NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    settlement_mode VARCHAR(10) NOT NULL DEFAULT 'LIVE',
    attempts INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tx_digest VARCHAR(128),
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (status IN ('PENDING', 'PROCESSING', 'SETTLED', 'FAILED')),
    CHECK (settlement_mode IN ('LIVE', 'MOCK'))
);

CREATE INDEX idx_outbox_queue
    ON outbox_event(status, next_retry_at, tenant_id, customer_id, chain_seq);
```

Tên cột có thể thay đổi khi triển khai, nhưng ý nghĩa, ràng buộc và tính toàn vẹn phải được giữ.

### 6.4. Bảo vệ ledger append-only

Database phải chặn `UPDATE` và `DELETE` trên `ledger_entry`, hoặc dùng quyền DB chỉ cho phép insert đối với role ứng dụng. Nếu cần đính chính, hệ thống tạo giao dịch mới có loại nghiệp vụ phù hợp; không sửa lịch sử.

```sql
CREATE OR REPLACE FUNCTION prevent_ledger_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entry is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_protect_ledger
BEFORE UPDATE OR DELETE ON ledger_entry
FOR EACH ROW EXECUTE FUNCTION prevent_ledger_modification();
```

### 6.5. Quy tắc nghiệp vụ

| Mã | Quy tắc |
|---|---|
| BR-01 | Điểm là số nguyên không âm; điểm issue được làm tròn xuống. |
| BR-02 | Tenant chỉ issue/redeem khi `APPROVED`. |
| BR-03 | Mỗi issue/redeem cần Idempotency-Key; cùng key/cùng payload trả kết quả cũ, khác payload trả `409`. |
| BR-04 | Balance không âm; redeem vượt balance trả `422` và không tạo giao dịch thành công. |
| BR-05 | Ledger append-only; đính chính bằng giao dịch mới. |
| BR-06 | Rule mới chỉ áp dụng cho giao dịch sau `effective_from`. |
| BR-07 | Merchant chỉ xem/sửa dữ liệu tenant của mình. |
| BR-08 | Chỉ đánh dấu `SETTLED` sau khi xác minh Sui và lưu digest. |
| BR-09 | API secret chỉ hiển thị một lần và lưu mã hóa. |
| BR-10 | Demo mặc định: 1.000 VND = 1 điểm; 1 điểm = 10 VND. |
| BR-11 | `settlementMode` không thay thế `settlementStatus`. |
| BR-12 | Timeout chain phải kiểm tra on-chain trước retry nếu có thể. |
| BR-13 | Event sau không được vượt event lỗi trước trong cùng tenant/customer. |

### 6.6. Trạng thái settlement

| Trạng thái | Ý nghĩa |
|---|---|
| `PENDING` | Giao dịch nghiệp vụ đã thành công, outbox đã ghi nhưng chưa xác nhận trên Sui. |
| `PROCESSING` | Worker đã nhận event và đang gửi/xác minh. |
| `SETTLED` | Sui đã xác nhận; digest đã lưu. |
| `FAILED` | Retry hết hoặc cần Admin xử lý; giao dịch nghiệp vụ off-chain vẫn tồn tại. |

Giao dịch nghiệp vụ thành công có thể ở `PENDING` hoặc `FAILED`; không được tự động rollback điểm chỉ vì settlement thất bại.

---

## 7. Mô tả use case chính

### UC-01 — Đăng ký tenant

| Mục | Nội dung |
|---|---|
| Actor | Merchant |
| Tiền điều kiện | Chưa có tài khoản tương ứng |
| Luồng chính | Nhập thông tin → validate → kiểm tra email → tạo tenant `PENDING` và user → thông báo chờ duyệt |
| Luồng thay thế | Dữ liệu sai `400`; email trùng `409` |
| Hậu điều kiện | Tenant tồn tại nhưng chưa được issue/redeem |

### UC-04 — Duyệt, từ chối, khóa tenant

| Mục | Nội dung |
|---|---|
| Actor | System Admin |
| Tiền điều kiện | Admin đã đăng nhập; tenant tồn tại |
| Luồng chính | Chọn tenant → chọn trạng thái → cập nhật → ghi audit log → invalid cache |
| Luồng thay thế | Chuyển trạng thái không hợp lệ `409` |
| Hậu điều kiện | Tenant được phép hoặc bị chặn giao dịch theo trạng thái |

### UC-09 — Issue điểm

| Mục | Nội dung |
|---|---|
| Actor | Merchant System |
| Tiền điều kiện | Tenant `APPROVED`; key active có scope `ISSUE`; rule tồn tại |
| Luồng chính | Gửi request → Gateway xác thực → kiểm tra idempotency → tính điểm → cập nhật balance, ledger, outbox trong một transaction → trả `PENDING` |
| Luồng thay thế | Sai auth `401`; sai scope/trạng thái `403`; rate limit `429`; payload sai `400`; cùng key khác payload `409` |
| Hậu điều kiện | Balance tăng, một ledger entry và một outbox event được tạo |

### UC-10 — Redeem điểm

| Mục | Nội dung |
|---|---|
| Actor | Merchant System |
| Tiền điều kiện | Tenant `APPROVED`; key active có scope `REDEEM` |
| Luồng chính | Gửi request → Gateway xác thực → cập nhật có điều kiện → ghi ledger/outbox → trả balance mới |
| Luồng thay thế | Balance không đủ `422`; không tạo ledger/outbox thành công |
| Hậu điều kiện | Balance giảm đúng số điểm, không âm |

### UC-16 — Settlement lên Sui theo batch

| Mục | Nội dung |
|---|---|
| Actor | Worker nội bộ; Sui Blockchain là hệ thống nhận kết quả |
| Tiền điều kiện | Có outbox event `PENDING` đến hạn |
| Luồng chính | Lock event theo thứ tự → chuyển `PROCESSING` → gom batch → gọi Signer → gửi PTB → xác minh → lưu digest → `SETTLED` |
| Luồng thay thế | Lỗi/timeout → đọc trạng thái on-chain → retry backoff; quá số lần → `FAILED` |
| Hậu điều kiện | Event `SETTLED` có digest hoặc `FAILED` có lỗi |

### UC-20 — Trả kết quả cũ theo Idempotency-Key

| Mục | Nội dung |
|---|---|
| Actor | Merchant System |
| Điều kiện | Key đã được dùng trước đó |
| Cùng payload | Trả response giao dịch cũ; không thay đổi balance |
| Khác payload | Trả `409 Conflict` |

### UC-21 — Kiểm tra on-chain trước retry

| Mục | Nội dung |
|---|---|
| Actor | Outbox Worker |
| Điều kiện | Signer/RPC timeout hoặc kết quả không xác định |
| Luồng | Đọc object/event/transaction → nếu đã áp dụng thì lưu digest và `SETTLED`; nếu chưa áp dụng thì retry; nếu vượt giới hạn thì `FAILED` |

---

## 8. Ma trận truy vết yêu cầu (Traceability Matrix)

### 8.1. FR với UR và Use Case

| FR | UR liên quan | Use case |
|---|---|---|
| FR-01 | UR-01 | UC-01 |
| FR-02 | UR-13 | UC-04 |
| FR-03 | UR-03 | UC-05, UC-06, UC-07 |
| FR-04 | UR-06 | UC-02 |
| FR-05 | UR-07 | UC-09, UC-14, UC-20 |
| FR-06 | UR-08 | UC-10, UC-14 |
| FR-07 | UR-05, UR-09 | UC-03, UC-11, UC-12 |
| FR-08 | UR-04 | UC-08, UC-14 |
| FR-09 | UR-17, UR-18 | UC-16, UC-21 |
| FR-10 | UR-17 | UC-16 |
| FR-11 | UR-10 | UC-09, UC-10, UC-11, UC-12 |
| FR-12 | UR-02, UR-05 | UC-03, UC-08, UC-11, UC-12 |
| FR-13 | UR-14 | UC-17, UC-18, UC-19 |
| FR-14 | UR-11 | UC-13 |
| FR-15 | UR-15 | UC-15, UC-18 |
| FR-17 | UR-21 | UC-22, UC-23, UC-24 |
| FR-18 | UR-19 | UC-25 |
| FR-19 | UR-20 | UC-26 |
| FR-27 | UR-16 | UC-27 |
| FR-28 | UR-12 | UC-28 |
| FR-29 | UR-22 | UC-29 |

### 8.2. FR với cách xác minh

| FR | Cách xác minh chính |
|---|---|
| FR-01 | Integration test đăng ký; demo bước 1 |
| FR-02 | Integration test trạng thái/audit; demo bước 2 |
| FR-03 | Unit test mã hóa; integration test revoke/rotate; secret scanning; demo bước 3 |
| FR-04 | Integration test JWT, RBAC và tenant isolation |
| FR-05 | Integration test issue, rule, idempotency; demo bước 4–5a |
| FR-06 | Concurrent test redeem; integration test insufficient balance; demo bước 6 |
| FR-07 | Integration test balance/history phân trang; demo bước 7 |
| FR-08 | Unit/integration test đổi rule; demo bước 3 |
| FR-09 | MockChainClient success/error/timeout; Testnet; demo bước 8 |
| FR-10 | `sui move test`; publish Testnet; Explorer digest |
| FR-11 | SDK unit test và HMAC test vector chung; demo bước 4 |
| FR-12 | Manual test Merchant Portal; demo bước 3 và 7 |
| FR-13 | Manual/integration test Admin Portal; demo bước 9 |
| FR-14 | Integration test sai HMAC, replay, timestamp, rate limit; demo bước 5b–5c |
| FR-15 | Integration test audit content; secret scanning; demo bước 9 |

### 8.3. NFR với cách xác minh

| NFR | Cách xác minh |
|---|---|
| NFR-01 | Load test 20 VU, ít nhất 1.000 request/endpoint, báo cáo p95 |
| NFR-02 | Security review, test RBAC/isolation, secret scanning |
| NFR-03 | PostgreSQL Testcontainers, 20 request redeem đồng thời |
| NFR-04 | GitHub Actions build/test/check bắt buộc xanh |
| NFR-05 | Digest và ảnh Sui Explorer Testnet; báo cáo tuần 7 |
| NFR-06 | Actuator health và metric smoke test |

### 8.4. Use case với actor

| Actor | Use case chính |
|---|---|
| Merchant | UC-01, UC-02, UC-03, UC-05, UC-06, UC-07, UC-08, UC-11, UC-12 |
| System Admin | UC-02, UC-04, UC-17, UC-18, UC-19 |
| Merchant System | UC-09, UC-10, UC-11, UC-12 |
| Sui Blockchain | Nhận settlement của UC-16 |
| AI Agent | UC-22, UC-23, UC-24 ở Should |

---

## 9. Phạm vi ngoài MVP và hướng phát triển

### 9.1. Should

- MCP Server chỉ cung cấp truy vấn balance/history/rule; không cho AI Agent issue/redeem trong MVP.
- Rule nâng cao, analytics, gas quota, webhook và DB-chain reconciliation.

### 9.2. Could

- Hóa đơn giả lập, anomaly detection, SSE, Java SDK prototype, offline-first, anchor hash, STAFF, MCP tool ghi có xác nhận, retry thủ công.

### 9.3. Won't trong MVP

- FR-16: zkLogin và sponsored transaction; MVP dùng Signer tự trả gas.
- FR-21: Walrus/Seal cho audit proof.
- FR-24: ClickHouse; analytics dùng PostgreSQL.
- Sui Mainnet và cam kết uptime production 99,9%.
- mTLS production giữa các service.
- Offline-first hoàn chỉnh.
- SDK production ngoài TypeScript.
- Module Move riêng cho từng tenant.
- Stripe thật.

### 9.4. Quy tắc hạ ưu tiên khi trễ

1. Cắt toàn bộ Could.
2. Cắt Should theo thứ tự FR-19, FR-27, FR-29, FR-28.
3. FR-12 có thể rút cấu hình rule trên Portal thành cấu hình qua API/Swagger.
4. FR-13 có thể rút Admin Portal còn tenant list, duyệt và audit log tối thiểu.
5. NFR-01 chuyển từ yêu cầu đạt sang yêu cầu đo, báo cáo và phân tích nếu chưa đạt.
6. FR-17 chỉ cắt sau khi các mục trên đã được xem xét vì MCP là phần quan trọng của đề tài.

---

## 10. Tiêu chí nghiệm thu MVP

MVP được coi là hoàn thành khi:

1. Tất cả FR/NFR Must đạt tiêu chí nghiệm thu.
2. Kịch bản demo bước 1–9 chạy được hoặc có bằng chứng thay thế hợp lệ cho Testnet.
3. Hệ thống khởi động local bằng Docker Compose theo README.
4. Move contract được publish trên Sui Testnet và package ID được ghi trong cấu hình/tài liệu.
5. Có ít nhất một settlement thực tế được xác minh bằng digest và ảnh Sui Explorer.
6. Có test cho issue, redeem, idempotency, HMAC, replay, RBAC, tenant isolation, concurrency và retry settlement.
7. GitHub Actions build/test thành công.
8. Repository không chứa secret/private key thật.
9. README mô tả cài đặt, chạy, cấu hình Testnet, test và xử lý lỗi phổ biến.
10. Hoàn thành các tài liệu bắt buộc:
    - URD.
    - SRS.
    - Kiến trúc và ADR.
    - UML: use case, sequence, component, ERD.
    - Database schema và Move contract specification.
    - SDK/MCP guide.
    - Test plan và test report.
    - Installation Guide.
    - User Manual.
    - OpenAPI/API documentation.

### Kịch bản demo tham chiếu

| Bước | Thao tác | Kết quả mong đợi | Yêu cầu |
|---|---|---|---|
| 1 | Merchant đăng ký tenant | Tenant `PENDING` | FR-01 |
| 2 | Admin duyệt tenant | Tenant `APPROVED` | FR-02, FR-04 |
| 3 | Merchant tạo API key, cấu hình rule | Secret chỉ hiện một lần; rule lưu thành công | FR-03, FR-08 |
| 4 | SDK issue 100.000 VND cho C1 | 100 điểm; settlement `PENDING` | FR-05, FR-11 |
| 5a | Gửi lại cùng idempotency key với nonce/chữ ký mới | Trả kết quả cũ; không cộng thêm | FR-05 |
| 5b | Gửi lại nguyên request cùng nonce | `401`; nonce đã dùng | FR-14 |
| 5c | Gửi chữ ký sai | `401` | FR-14 |
| 6 | Redeem 30, sau đó redeem 100 | Còn 70; request thứ hai `422` | FR-06 |
| 7 | Mở Portal xem balance/history | Có giao dịch, settlement status, digest nếu đã settle | FR-07, FR-12 |
| 8 | Mở digest trên Sui Explorer | Giao dịch Testnet được tìm thấy | FR-09, FR-10, NFR-05 |
| 9 | Admin mở dashboard/audit log | Có số liệu tenant, settlement, audit | FR-13, FR-15 |
| 10 | AI Agent hỏi balance qua MCP | Trả balance đúng trong tenant được phép | FR-17, Should |

Nếu Testnet chậm/lỗi, dùng `MockChainClient`, video hoặc bằng chứng đã lưu. Portal phải phân biệt rõ `settlementMode = MOCK`; không gán digest cũ cho giao dịch mock mới.
