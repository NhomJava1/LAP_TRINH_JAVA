# Kiến trúc hệ thống OctaPoint

| Thuộc tính | Giá trị |
|---|---|
| Phiên bản | 1.0 |
| Ngày | 2026-11-06 |
| Trạng thái | Kiến trúc mục tiêu cho MVP; một số container/chức năng chưa được tích hợp |
| Phương pháp | IEEE 1471 / arc42 |

## 1. Giới thiệu và mục tiêu

OctaPoint là nền tảng Credit-as-a-Service đa tenant cho phép Merchant System tích hợp tích lũy và quy đổi điểm thưởng. Kiến trúc hướng tới phản hồi nghiệp vụ nhanh, cô lập dữ liệu tenant, lưu vết giao dịch và settlement có thể kiểm chứng trên Sui Testnet.

Nguyên tắc cốt lõi:

- PostgreSQL là nguồn dữ liệu có thẩm quyền (SSOT) cho số dư, ledger và trạng thái nghiệp vụ.
- Issue/redeem ghi thay đổi số dư, ledger và outbox trong cùng một transaction.
- Blockchain settlement chạy bất đồng bộ, không nằm trong đường phản hồi đồng bộ của request.
- Khóa ký chỉ nằm trong Signer; backend Core gọi Signer qua mạng nội bộ.
- Sui được dùng để settlement và đối soát, không thay PostgreSQL để trả số dư khả dụng.

Tài liệu này mô tả thiết kế mục tiêu. Tại thời điểm viết, backend mới có một số khung thành phần; API số dư hiện là mock, Signer mới hỗ trợ endpoint thử nghiệm issue trực tiếp, và Move contract hiện tại mới có `AdminCap`, shared `Balance` cùng thao tác `issue`. Chưa có luồng Worker-outbox-to-Signer hoàn chỉnh.

## 2. Ràng buộc

| Lĩnh vực | Ràng buộc |
|---|---|
| Ngôn ngữ và framework Core | Java 21, Spring Boot 3.3.4 |
| Cơ sở dữ liệu | PostgreSQL 15 trong Docker Compose; Flyway quản lý migration PostgreSQL |
| Cache/trạng thái bảo mật | Redis 7 |
| Signer | Dịch vụ Node.js/TypeScript riêng, dự kiến chỉ được gọi nội bộ |
| Smart contract | Sui Move; MVP nhắm tới Sui Testnet |
| Frontend | Next.js, TypeScript, Tailwind CSS và shadcn/ui |
| SDK | TypeScript chạy server-side; không đặt API secret trong trình duyệt |
| API merchant | REST, xác thực HMAC-SHA256 theo hợp đồng API thống nhất |
| Triển khai | Docker Compose cho môi trường phát triển/demo |
| Lưu trữ tiền điểm | Số điểm và sequence được lưu dưới dạng số nguyên; tránh sai số dấu phẩy động |

Profile local hiện dùng H2; cấu hình container `core` dùng PostgreSQL và bật Flyway. Các migration và hành vi của H2/PostgreSQL cần được kiểm thử riêng trước khi xem hai profile là tương đương.

## 3. Ngữ cảnh hệ thống

### 3.1 Actor và hệ thống bên ngoài

| Actor/hệ thống | Tương tác |
|---|---|
| Merchant Portal | Merchant đăng ký, quản lý tenant/API key/rule và tra cứu dữ liệu thuộc tenant |
| Admin Portal | Admin duyệt hoặc khóa tenant, giám sát settlement và audit |
| Merchant System (POS/e-commerce) | Gọi REST API trực tiếp hoặc qua SDK TypeScript server-side |
| Sui Blockchain (Testnet) | Nhận giao dịch settlement và cung cấp trạng thái/digest phục vụ đối soát |
| AI Agent (MCP) | Actor mở rộng; truy vấn giới hạn theo quyền/tenant, chưa thuộc phạm vi triển khai hiện tại |

### 3.2 Ranh giới tin cậy

- Request từ Merchant System là không tin cậy cho đến khi hoàn tất xác thực, phân quyền và kiểm tra tenant.
- Signer không được mở trực tiếp ra Internet; chỉ Core/Worker được cấp quyền gọi.
- Private key, khóa mã hóa và token dịch vụ là cấu hình bí mật, không ghi vào source control hoặc log.
- Redis không phải nguồn dữ liệu số dư. Nếu Redis không sẵn sàng, thao tác cần nonce/replay protection phải thất bại đóng (reject).
- Blockchain là hệ thống ngoài có thể chậm, lỗi hoặc trả kết quả không xác định; không giữ transaction DB mở trong lúc gọi RPC.

## 4. Kiến trúc tổng thể — Container view

```text
Merchant Portal / Admin Portal / Merchant System + server-side SDK
                              |
                         HTTPS / REST
                              v
     +--------------------------------------------------+
     | Gateway / API (Spring Boot)                     |
     | Filter bảo mật, validation, routing              |
     +-------------------------+------------------------+
                               |
                 +-------------+-------------+
                 v                           v
         Core Service                    Redis 7
         tenant / credit /                nonce, rate limit,
         balance / ledger                 cache (không SSOT)
                 |
                 | một PostgreSQL transaction
                 v
          PostgreSQL 15  <---- Settlement Worker
          balance, ledger,             |
          idempotency, outbox           | mạng nội bộ
                                        v
                                  Signer (Node.js/TS)
                                        |
                                        | ký và gửi PTB
                                        v
                                Sui Testnet / Move
```

| Container | Trách nhiệm và giao tiếp |
|---|---|
| Gateway/API | Điểm vào REST. Chạy trong Spring Boot cùng Core theo mô hình modular monolith; áp dụng filter bảo mật, kiểm tra đầu vào và ánh xạ lỗi HTTP. |
| Core Service | Xử lý tenant, rule, issue/redeem, idempotency và giao dịch nghiệp vụ. Cập nhật balance, ledger và outbox nguyên tử trong PostgreSQL. |
| Settlement Worker | Claim outbox theo batch, bảo toàn thứ tự theo tenant/customer, gọi Signer ngoài transaction DB, xác minh kết quả và cập nhật trạng thái settlement. Đây là container/tiến trình mục tiêu, chưa có trong Compose hiện tại. |
| Signer | Dịch vụ Node.js/TypeScript giữ quyền ký, xây dựng giao dịch Sui và trả digest/kết quả cho Worker. Bản hiện tại mới có endpoint thử nghiệm `/internal/issue`, không phải adapter batch. |
| PostgreSQL | Lưu số dư có thẩm quyền, ledger append-only, idempotency, outbox, chain transaction và audit log. |
| Redis | Dùng cho nonce chống replay, rate limit và cache. Không lưu trữ dữ liệu nghiệp vụ có thẩm quyền. |
| Sui Testnet | Nhận settlement và làm nguồn xác minh giao dịch on-chain; không dùng để trả số dư tức thời trong API. |
| Frontend | Merchant/Admin portal dùng Next.js. Gọi Core qua API; không được giữ merchant API secret ở phía client. |

Trong repository hiện tại, `docker-compose.yml` khởi chạy PostgreSQL, Redis, Sui CLI và Core. Frontend và Signer được chạy riêng; Worker chưa được cấu hình.

## 5. Luồng dữ liệu chính

### 5.1 Issue

1. Merchant System tạo request có idempotency key, timestamp, nonce và chữ ký HMAC.
2. Gateway kiểm tra giới hạn theo IP, API key, timestamp, chữ ký, nonce và scope. Redis không khả dụng thì từ chối request cần kiểm tra nonce.
3. Core xác minh tenant/rule và xử lý idempotency. Key trùng nội dung trả lại phản hồi đã lưu; key trùng nhưng nội dung khác trả `409 Conflict`.
4. Trong một PostgreSQL transaction, Core cộng balance, cấp `chain_seq`, thêm ledger entry và outbox event. Commit thành công là kết quả nghiệp vụ; không chờ Sui.
5. API trả kết quả issue cùng trạng thái settlement ban đầu `PENDING`.

Chi tiết luồng dự kiến: [issue activity](../4.ActivityDiagram/issue-activity.puml) và [issue sequence](../5.Sequence/issue_seq.txt).

### 5.2 Redeem

1. Gateway áp dụng cùng các bước xác thực, chống replay, rate limit và kiểm tra scope `REDEEM`.
2. Core xác minh tenant và customer, sau đó xử lý idempotency.
3. Trong transaction, cập nhật balance có điều kiện `amount >= pointsToRedeem`. Nếu không đủ điểm, trả `422 INSUFFICIENT_BALANCE` và không ghi ledger/outbox cho request bị từ chối.
4. Nếu thành công, ghi ledger `REDEEM` và outbox event trong cùng transaction rồi trả kết quả cùng settlement `PENDING`.

Chi tiết luồng dự kiến: [redeem activity](../4.ActivityDiagram/redeem-activity.puml) và [redeem sequence](../5.Sequence/redeem_seq.txt).

### 5.3 Settlement

1. Worker định kỳ claim các outbox event đủ điều kiện bằng truy vấn có khóa `FOR UPDATE SKIP LOCKED`; chỉ claim event đầu tiên chưa được xử lý của mỗi tenant/customer.
2. Worker ghi trạng thái `PROCESSING` rồi commit, giải phóng kết nối DB trước khi gọi dịch vụ ngoài.
3. Worker gửi batch đến Signer qua mạng nội bộ. Signer ký và gửi giao dịch lên Sui bằng ví platform.
4. Worker lưu digest/chi phí gas và chuyển sự kiện sang `SETTLED` sau khi xác minh kết quả.
5. Khi timeout hoặc kết quả không rõ, Worker kiểm tra trạng thái on-chain trước khi retry để tránh giao dịch lặp. Retry dùng exponential backoff; tối đa 5 lần theo quyết định MVP.
6. Sự kiện `FAILED` chặn các sự kiện sau của cùng tenant/customer cho tới khi được xử lý; customer khác vẫn tiếp tục.

Luồng này là thiết kế mục tiêu; hiện chưa được tích hợp end-to-end.

## 6. Quyết định kiến trúc

| ADR | Quyết định |
|---|---|
| [ADR-0001](../adr/0001-separate-chain-client-from-signer.md) | Tách `ChainClient` khỏi Signer để Core phụ thuộc vào abstraction thay vì chi tiết ký giao dịch. |
| [ADR-0002](../adr/0002-outbox-pattern-for-settlement.md) | Transactional Outbox để tách phản hồi nghiệp vụ khỏi settlement blockchain. |
| [ADR-0003](../adr/0003-hmac-api-authentication.md) | HMAC-SHA256, timestamp và nonce cho xác thực Merchant API. |
| [ADR-0004](../adr/0004-signer-pays-gas.md) | Signer tự ký và trả gas trong MVP, không dùng sponsored transaction. |

Các ADR mô tả quyết định/đích thiết kế. Trước khi tích hợp client, cần hợp nhất khác biệt giữa đặc tả HMAC hiện có, ADR-0003 và header/canonical string trong mã hiện tại.

## 7. Chiến lược triển khai

- **Local:** Docker Compose cho PostgreSQL 15 và Redis 7; Core có thể chạy trên host với H2 hoặc trong container với PostgreSQL.
- **Profile:** cấu hình mặc định và `local` hiện dùng H2; Compose đặt `SPRING_PROFILES_ACTIVE=prod`, kết nối PostgreSQL và bật Flyway.
- **Signer:** chạy riêng trong quá trình phát triển; cần cấu hình Testnet, object/package ID và service token. Không đưa khóa riêng vào image hoặc repository.
- **Sui CLI:** chạy qua service Compose `sui-cli`, volume cấu hình CLI riêng.
- **Frontend:** `npm run dev` trong `frontend/`; Compose hiện không khởi chạy frontend.
- **Production:** Docker Compose là cấu hình học tập/demo, không phải baseline HA. Cần bổ sung health check, secret manager, TLS termination, backup/restore, resource limits và giám sát vận hành trước khi dùng production.

## 8. Bảo mật

- Merchant API dùng HMAC-SHA256 với API key ID, timestamp, nonce, chữ ký bao phủ method/path/query/body. Timestamp mục tiêu lệch tối đa ±300 giây; nonce lưu Redis với TTL 600 giây.
- Idempotency chống tạo giao dịch nghiệp vụ trùng; nonce riêng biệt chống phát lại request đã ký.
- API secret được lưu mã hóa AES-256-GCM; master key lấy từ biến môi trường/secret manager và không ghi log.
- JWT và RBAC bảo vệ Portal; scope giới hạn API key theo hành động. Kiểm tra tenant phải được thực hiện ở phía server.
- Rate limit mục tiêu gồm giới hạn theo IP và theo API key; Redis outage phải fail closed cho kiểm tra replay.
- Signer chỉ nhận lời gọi nội bộ có service token; private key chỉ được nạp trong môi trường Signer.
- PostgreSQL là nguồn số dư có thẩm quyền; ledger append-only và constraint database giúp bảo vệ tính toàn vẹn.

Đây là mục tiêu bảo mật. Mã HMAC hiện có vẫn là khung thử nghiệm: header và canonical string không khớp ADR-0003, đang dùng secret giả, và chưa nên được xem là xác thực production.

## 9. Giám sát

- Spring Boot Actuator cung cấp health/info; chỉ expose endpoint cần thiết và bảo vệ endpoint quản trị trong môi trường triển khai.
- Theo dõi độ sâu hàng đợi outbox, tuổi event PENDING, số event PROCESSING quá hạn, số lần retry, FAILED theo customer, latency Signer/RPC, gas đã dùng và số dư gas của ví platform.
- Ghi log có cấu trúc với correlation ID, tenant ID đã được kiểm soát và outbox event ID; không ghi API secret, chữ ký, private key hay nội dung nhạy cảm.
- Cảnh báo khi outbox queue tăng bất thường, có event FAILED hoặc gas thấp.

## 10. Hạn chế đã biết và hướng phát triển

### Hạn chế hiện tại

- API số dư hiện trả dữ liệu mock; luồng issue/redeem Core, persistence repository và Worker chưa hoàn chỉnh.
- `HmacFilter` và `ApiKeyService` hiện là khung; chưa đáp ứng đầy đủ hợp đồng HMAC, phân quyền, nonce ordering và quản lý khóa production.
- Signer mới gọi thao tác `issue` trên Move contract thử nghiệm; chưa nhận batch từ outbox.
- Move contract hiện tại chưa có account theo customer, `seq` chống trùng, redeem hay sự kiện đủ để đối soát theo event nghiệp vụ.
- TypeScript SDK, JWT/RBAC đầy đủ, Portal hoàn chỉnh, admin retry và đối soát chưa được triển khai end-to-end.
- H2 không thay thế kiểm thử tích hợp PostgreSQL/Flyway; Redis và Sui Testnet cần được kiểm thử trong môi trường riêng.

### Hướng phát triển

1. Hoàn tất mô hình tenant/customer, repository và transaction balance-ledger-idempotency-outbox.
2. Thống nhất đặc tả HMAC, cập nhật filter và SDK cùng test vectors.
3. Xây dựng Settlement Worker có khóa/lease phục hồi PROCESSING, retry, kiểm tra giao dịch chưa rõ kết quả và xử lý FAILED.
4. Mở rộng Move contract cho account/sequence và issue/redeem; tích hợp Signer theo batch nội bộ.
5. Bổ sung kiểm thử tích hợp PostgreSQL/Redis/Sui, Actuator metrics, health check và quy trình vận hành.
6. Phát triển các hạng mục mở rộng theo MoSCoW trong [Requirement Index](../1.SRS/requiment-index.md), như gas quota, webhook, reconciliation và MCP.
