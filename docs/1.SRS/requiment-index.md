# OctaPoint: Requirement Index, MVP Scope và Kịch bản demo

| **Phiên bản** | 0.2 |
|---|---|
| **Ticket** | SCRUM-11 |
| **Trạng thái** | Dự thảo sửa đổi từ v0.1, chờ review của nhóm |

---

## 0. Nhật ký thay đổi so với v0.1

| # | Mục | Thay đổi | Lý do |
|---|---|---|---|
| 1 | 2.1 | Thêm cột "Cách xác minh" cho mọi yêu cầu Must | Mục 1.1 yêu cầu mỗi Must có tiêu chí nghiệm thu **và** cách xác minh, v0.1 mới có tiêu chí |
| 2 | 2.2 (FR-09/10) | Thay phần "chờ TV cho ý kiến" bằng một đề xuất thiết kế cụ thể (seq theo tài khoản on-chain). Giữ phương án B nếu TV kết luận không khả thi | Quyết định đã chốt ở nhóm: Option A, đồng thời chặn mint trùng khi retry |
| 3 | 2.3 → 2.5 | Chuyển FR-16 (zkLogin, sponsored tx), FR-21 (Walrus), FR-24 (ClickHouse) sang Won't. Giữ nguyên mã FR | Đã thống nhất bỏ luồng sponsor (ADR-08) và không dùng ClickHouse. Tránh mâu thuẫn giữa tài liệu và kiến trúc |
| 4 | 2.3, 2.4 | Thêm FR-27 (hạn mức gas theo tenant), FR-28 (webhook), FR-29 (đối soát DB-chain) vào Should; FR-30 (anchor hash), FR-31 (vai trò STAFF), FR-32 (MCP tool ghi) vào Could | Các hạng mục này có trong đề xuất gốc hoặc backlog Jira nhưng chưa có mã yêu cầu |
| 5 | 2.1 (FR-03) | Định nghĩa "xoay vòng" = tạo key mới + thu hồi key cũ | Tránh làm thêm cơ chế grace period ngoài phạm vi MVP |
| 6 | 2.1 (FR-13) | Thêm: Admin xem danh sách giao dịch FAILED. Retry thủ công đưa sang Could | Trạng thái FAILED "cần xử lý thủ công" nhưng chưa có yêu cầu cho Admin thấy nó |
| 7 | 3 (NFR-03) | Làm rõ kết quả đúng: 10 request thành công, 10 request bị từ chối, số dư cuối bằng 0 | Tiêu chí v0.1 ("không vượt 100") chưa đủ để kiểm thử tự động có đáp án duy nhất |
| 8 | 3 (NFR-05) | Chụp bằng chứng Explorer đầu tiên ngay khi settlement chạy được, rồi cập nhật lại ở báo cáo tuần 7 | Chỉ có bằng chứng ở tuần 7 là quá muộn nếu Testnet lỗi |
| 9 | 3 (NFR-01) | Đo sớm từ Sprint 4. Nêu rõ môi trường đo | NFR Must phụ thuộc phần cứng laptop, cần phát hiện sớm |
| 10 | 3.1 (mới) | Thêm danh sách tài liệu bắt buộc theo yêu cầu môn học (URD, SRS, UML, test plan...) | v0.1 chỉ nhắc README, API docs, kiến trúc, hướng dẫn demo |
| 11 | 4 | Thêm bước kiểm tra replay đúng chữ ký (401 nonce), nhắc rehearse độ trễ settlement, thêm bước 10 tùy chọn cho MCP | Demo cần chứng minh replay và idempotency là hai cơ chế riêng |
| 12 | 5 | Tách `settlementMode` (LIVE/MOCK) khỏi `settlementStatus` | Tránh trộn "chế độ chạy" với "trạng thái settlement" |
| 13 | 6, 7 | Bổ sung tiêu chí hoàn thành; thêm mục 7 "Quy tắc hạ ưu tiên khi trễ" và mục 8 "Câu hỏi mở" | Quyết định cắt phạm vi từ trước, không quyết khi đã trễ |

---

## 1. Quy ước và giả định kiến trúc

### 1.1. Quy ước

- FR-xx: yêu cầu chức năng.
- NFR-xx: yêu cầu phi chức năng.
- D-xx: sản phẩm tài liệu bắt buộc.
- Mỗi ticket Jira gắn mã yêu cầu liên quan và nhãn MoSCoW.
- Mỗi yêu cầu Must phải có tiêu chí nghiệm thu và cách xác minh.
- Một ticket có thể liên kết với nhiều yêu cầu, nhưng phải chỉ rõ từng yêu cầu được xử lý như thế nào.
- Mã yêu cầu không bị đổi nghĩa. Yêu cầu bị bỏ được chuyển sang Won't và giữ nguyên mã.

### 1.2. Quyết định kiến trúc cho MVP

- Core backend: Java Spring Boot.
- PostgreSQL: lưu tenant, tài khoản, rule, số dư nghiệp vụ, sổ cái và transactional outbox.
- Redis: cache, nonce chống replay và rate limit. Redis không phải nguồn dữ liệu có thẩm quyền về số dư.
- Sui Signer: dịch vụ TypeScript riêng, chỉ nhận request từ backend được xác thực và không mở trực tiếp ra Internet.
- Gas: Signer vừa là người gửi vừa trả gas. Không dùng luồng sponsored transaction hai chữ ký trong MVP.
- Blockchain: Sui Testnet.
- Smart contract: Sui Move.
- SDK: TypeScript chạy trong môi trường Node.js hoặc backend server. Không nhúng API secret vào frontend chạy trong trình duyệt.
- Frontend: TypeScript.
- API công khai: REST. gRPC không thuộc phạm vi bắt buộc của MVP.
- Giảng viên đã xác nhận: core web app là Spring Boot, các phần còn lại được chọn công nghệ tự do.

### 1.3. Quy tắc nhất quán dữ liệu

PostgreSQL là nguồn dữ liệu chính để xử lý số dư khả dụng và ghi nhận giao dịch nghiệp vụ tức thời. Thao tác issue hoặc redeem phải cập nhật số dư, sổ cái và outbox trong cùng một transaction cơ sở dữ liệu.

`ledger_entry` chỉ lưu các giao dịch nghiệp vụ đã thành công và không bao giờ bị sửa hoặc xóa (append-only). Request bị từ chối không tạo bản ghi giao dịch trong ledger. Những request bị từ chối có ý nghĩa bảo mật hoặc cần truy vết, chẳng hạn sai chữ ký, replay hoặc truy cập trái quyền, được ghi vào `audit_log` hoặc security log phù hợp.

Trạng thái settlement được quản lý riêng với kết quả nghiệp vụ (trường `settlementStatus`):

- **PENDING**: sự kiện đã được ghi vào outbox nhưng chưa xác nhận hoàn tất trên Sui.
- **SETTLED**: giao dịch đã được xác minh trên Sui và transaction digest đã được lưu.
- **FAILED**: settlement thất bại sau khi áp dụng chính sách retry hoặc cần xử lý thủ công.

Giao dịch nghiệp vụ đã thành công có thể ở trạng thái settlement PENDING hoặc FAILED. Không được đánh dấu SETTLED trước khi xác minh được giao dịch trên chuỗi.

Worker đọc outbox và gửi sự kiện lên Sui theo lô. Mỗi sự kiện có `eventId` duy nhất. Worker phải lưu batch, kết quả gửi và transaction digest để hỗ trợ retry, truy vết và đối soát. Trước khi gửi lại một sự kiện có kết quả không rõ ràng do timeout, worker phải kiểm tra trạng thái trên Sui nếu có thể. Outbox phía backend không tự nó bảo đảm idempotency ở cấp smart contract khi kết quả giao dịch on-chain chưa rõ.

Redis chỉ hỗ trợ hiệu năng. Nếu cache không đồng bộ với PostgreSQL, hệ thống phải đọc lại số dư đúng từ PostgreSQL.

---

## 2. Yêu cầu chức năng

### 2.1. Must, bắt buộc cho MVP

| Mã | Yêu cầu | Tiêu chí nghiệm thu | Cách xác minh |
|---|---|---|---|
| FR-01 | Merchant tự đăng ký tenant | Đăng ký thành công tạo tenant ở trạng thái PENDING. Tenant chưa được issue hoặc redeem điểm khi chưa được duyệt. | Integration test; demo bước 1 |
| FR-02 | Admin duyệt, từ chối hoặc khóa tenant | Admin có thể thay đổi trạng thái tenant. Tenant bị từ chối hoặc khóa không được thực hiện giao dịch điểm. Thay đổi trạng thái được ghi vào audit log. | Integration test; demo bước 2 |
| FR-03 | Cấp, xoay vòng và thu hồi API key | Secret chỉ hiển thị một lần khi tạo hoặc xoay vòng. Xoay vòng = tạo key mới và thu hồi key cũ. Secret được mã hóa khi lưu trữ và không xuất hiện trong log. API key đã thu hồi không thể xác thực request. | Unit test mã hóa; integration test thu hồi; secret scanning; demo bước 3 |
| FR-04 | Đăng nhập và phân quyền | Portal dùng JWT với hai vai trò ADMIN và MERCHANT. Merchant chỉ được truy cập dữ liệu thuộc tenant của mình. | Integration test RBAC và cô lập tenant (tenant A không đọc được dữ liệu tenant B) |
| FR-05 | Issue điểm qua API | Điểm được tính từ giá trị đơn hàng và rule của tenant. Request có idempotency key. Request hợp lệ được ghi vào ledger trong một transaction cơ sở dữ liệu. Khách hàng chưa tồn tại được tạo ở lần issue đầu tiên. | Integration test; demo bước 4, 5 |
| FR-06 | Redeem điểm qua API | Chỉ cho phép quy đổi khi số dư khả dụng đủ. Không cho phép số dư âm, kể cả khi có nhiều request đồng thời. | Test đồng thời (NFR-03); demo bước 6 |
| FR-07 | Tra cứu số dư và lịch sử | Merchant có thể truy vấn số dư và lịch sử giao dịch của khách hàng thuộc tenant của mình. Kết quả hiển thị settlement status và digest nếu đã settle. | Integration test; demo bước 7 |
| FR-08 | Rule earn/redeem cơ bản | Mỗi tenant có thể cấu hình tỷ lệ tích lũy và giá trị quy đổi điểm (số nguyên dương). Rule mới chỉ áp dụng cho giao dịch phát sinh sau thời điểm cập nhật. | Unit test tính điểm; integration test đổi rule; demo bước 3 |
| FR-09 | Settlement bất đồng bộ lên Sui | Giao dịch nghiệp vụ được ghi vào outbox. Worker gom sự kiện thành batch, retry khi lỗi và lưu digest khi settlement được xác minh thành công. Mỗi sự kiện có `eventId` duy nhất để truy vết xuyên suốt backend và blockchain. | Integration test với MockChainClient (thành công, lỗi, timeout); test trên Testnet; demo bước 8 |
| FR-10 | Smart contract Sui Move | Hợp đồng xác minh quyền gọi, ghi nhận giao dịch issue/redeem được settlement, phát event kèm `eventId` và thông tin giao dịch cần thiết, và dùng AdminCap cho thao tác quản trị. Cơ chế chống áp dụng trùng theo mục 2.2. | `sui move test`; publish Testnet; digest trên Explorer |
| FR-11 | SDK TypeScript | SDK cung cấp issue, redeem, balance và history. SDK tự tạo chữ ký HMAC và hỗ trợ idempotency key. SDK chỉ được dùng với secret trong môi trường server-side. | Unit test với test vector HMAC dùng chung với backend; demo bước 4 |
| FR-12 | Merchant Portal | Merchant có thể xem trạng thái tenant, quản lý API key, cấu hình rule và xem lịch sử giao dịch. | Demo bước 3, 7; kiểm thử thủ công theo test case |
| FR-13 | Admin Portal | Admin có thể duyệt tenant, xem số tenant, số giao dịch, trạng thái settlement (kể cả danh sách giao dịch FAILED) và audit log cơ bản. | Demo bước 9; kiểm thử thủ công |
| FR-14 | Gateway bảo mật API | Xác minh HMAC-SHA256, kiểm tra timestamp, chống replay và giới hạn tốc độ request. Request sai chữ ký bị từ chối với HTTP 401. Request vượt hạn mức nhận HTTP 429. | Integration test từng trường hợp; demo bước 5 |
| FR-15 | Audit log | Ghi nhận người thực hiện, hành động, đối tượng, thời điểm, kết quả và mã tương quan request đối với thao tác nhạy cảm. Không ghi API secret, private key hoặc JWT vào log. | Integration test nội dung log; secret scanning; demo bước 9 |

### 2.2. Quy tắc nghiệp vụ chi tiết cho Must

#### FR-05. Issue điểm

API issue nhận tối thiểu các trường:

- `customerId`
- `orderId` hoặc `businessTransactionId`
- `amountVnd`
- `Idempotency-Key` trong HTTP header

`amountVnd` phải là số nguyên dương. Hệ thống lấy rule hiện tại của tenant để tính điểm. Điểm được làm tròn xuống theo quy tắc đã thống nhất.

Ví dụ: tỷ lệ 1.000 VND = 1 điểm. Đơn hàng 100.000 VND được cấp 100 điểm.

Nếu cùng một idempotency key và cùng payload được gửi lại, hệ thống trả về kết quả giao dịch ban đầu mà không cộng thêm điểm. Nếu cùng key nhưng payload khác, hệ thống từ chối với HTTP 409.

#### FR-06. Redeem điểm

API redeem nhận tối thiểu `customerId`, `orderId` và `pointsToRedeem`.

Hệ thống kiểm tra trạng thái tenant, dữ liệu đầu vào và số dư khả dụng trước khi ghi nhận giao dịch. Việc kiểm tra và trừ điểm phải được thực hiện nguyên tử trong PostgreSQL, sử dụng cơ chế khóa hoặc cập nhật có điều kiện phù hợp.

Nếu số dư không đủ, hệ thống từ chối request (HTTP 422), không trừ điểm và không tạo bản ghi redeem trong ledger. Request bị từ chối có thể được ghi vào audit log hoặc security log nếu cần truy vết.

#### FR-08. Rule tích lũy và quy đổi

Rule tối thiểu gồm:

- `earnVndPerPoint`: số VND cần chi tiêu để nhận một điểm.
- `redeemValueVndPerPoint`: giá trị quy đổi của một điểm tính theo VND.

Giá trị mặc định dùng trong demo:

- 1.000 VND = 1 điểm.
- 1 điểm = 10 VND giá trị quy đổi.

Đây là giá trị cấu hình phục vụ demo, không phải chính sách cố định cho mọi merchant.

#### FR-09 và FR-10. Settlement lên Sui

Mỗi sự kiện issue/redeem được ghi vào transactional outbox cùng transaction ghi sổ cái PostgreSQL.

Worker flush batch theo chu kỳ cấu hình từ 5 đến 10 giây khi có sự kiện đang chờ. Đây là chu kỳ gom và gửi batch, không phải cam kết rằng Sui sẽ xác nhận giao dịch trong 5 đến 10 giây.

Worker chỉ đánh dấu batch SETTLED sau khi xác minh giao dịch thành công trên Sui và lưu transaction digest. Nếu settlement thất bại, sự kiện được retry theo chính sách đã định. Với timeout hoặc kết quả không xác định, worker phải kiểm tra trạng thái giao dịch trên Sui trước khi gửi lại nếu thông tin cần thiết có sẵn.

**Đề xuất thiết kế chống áp dụng trùng ở cấp contract (Option A). TV xác minh tính khả thi trước khi merge SCRUM-11, thiết kế chi tiết chốt trong ADR Sprint 3:**

- Mỗi cặp (tenant, customer) có một đối tượng `CreditAccount` on-chain, mang trường `seq` (số nguyên, bắt đầu từ 0).
- Mỗi `ledger_entry` có `chain_seq` (0, 1, 2...) theo từng customer, được cấp nguyên tử cùng lúc với cập nhật số dư trong PostgreSQL.
- Lệnh mint/redeem gửi lên chain kèm `seq`. Contract từ chối nếu `seq` khác `seq` hiện tại của tài khoản, và tăng `seq` sau khi áp dụng thành công.
- Khi gửi lại sau timeout, nếu lệnh đã được áp dụng thì chain từ chối do sai `seq`. Worker kiểm tra trạng thái on-chain và đánh dấu SETTLED thay vì áp dụng lần hai.
- `eventId` (trùng `id` của ledger entry) vẫn được phát trong event để truy vết và đối soát. Contract không cần lưu tập `eventId` đã xử lý.
- Batch = nhiều lệnh gom vào một lần gửi. MVP dùng một worker xử lý tuần tự theo từng customer để tránh tranh chấp gas coin của Signer.

TV cần xác minh trong tài liệu Sui/Move chính thức: (1) object `CreditAccount` do platform sở hữu và cách chuyển quyền sở hữu, (2) giới hạn và tính nguyên tử của một giao dịch nhiều lệnh (PTB), (3) cách đọc `seq` hiện tại của object, (4) xung đột gas coin khi gửi song song.

**Phương án B, nếu TV kết luận Option A không khả thi trong MVP:** không triển khai chống trùng bằng seq hoặc `eventId` trong Move. Rủi ro duplicate on-chain khi kết quả giao dịch không xác định phải được ghi trong tài liệu thiết kế, đồng thời worker phải có bước đối chiếu trước khi retry. Không được tuyên bố rằng outbox một mình bảo đảm exactly-once execution trên blockchain.

Trong MVP, số dư API được đọc từ sổ cái PostgreSQL. Dữ liệu trên Sui dùng để xác minh settlement và đối soát. Giới hạn này phải được ghi rõ trong báo cáo kiến trúc.

#### FR-14. HMAC và chống replay

Chữ ký HMAC phải được tính từ chuỗi dữ liệu chuẩn hóa, bao gồm tối thiểu HTTP method, path, query chuẩn hóa, timestamp, nonce và hash của body.

- Timestamp phải nằm trong khoảng cho phép đã cấu hình, mặc định 5 phút.
- Nonce không được sử dụng lại trong khoảng hiệu lực.
- Request có chữ ký sai hoặc timestamp không hợp lệ bị từ chối.
- Request vượt hạn mức trả HTTP 429.
- Request lặp lại nghiệp vụ có thể nhận lại kết quả cũ bằng idempotency key, nhưng phải dùng timestamp, nonce và chữ ký hợp lệ cho request mới.

Chống replay và idempotency là hai cơ chế riêng biệt. Không dùng một cơ chế để thay thế cơ chế còn lại.

### 2.3. Should, thực hiện khi toàn bộ Must đã đạt

| Mã | Yêu cầu | Giới hạn triển khai |
|---|---|---|
| FR-17 | MCP Server | Triển khai từ 3 đến 5 công cụ truy vấn như số dư, lịch sử và rule. Mỗi request phải được giới hạn theo tenant, dùng API key quyền READ. AI Agent không thực hiện issue hoặc redeem trong MVP. |
| FR-18 | Rule nâng cao | Bổ sung hạn dùng, tier và multiplier. |
| FR-19 | Analytics cơ bản | Dùng truy vấn tổng hợp PostgreSQL để thống kê tenant, giao dịch và tỷ lệ quy đổi. |
| FR-27 | Hạn mức gas theo tenant | Đếm gas đã dùng theo tenant, vượt hạn mức thì dừng đưa lệnh lên chain (giao dịch nghiệp vụ vẫn thành công, settlement chờ nâng hạn mức). |
| FR-28 | Webhook | Gửi webhook khi giao dịch SETTLED, ký HMAC, có retry. SDK có hàm xác minh chữ ký. |
| FR-29 | Đối soát DB và chain | Job định kỳ so số dư PostgreSQL với `CreditAccount` on-chain, lưu kết quả, cảnh báo khi lệch. |

### 2.4. Could, chỉ thực hiện nếu còn thời gian

| Mã | Yêu cầu |
|---|---|
| FR-20 | Gói dịch vụ và hóa đơn giả lập trong PostgreSQL. Stripe thật không thuộc phạm vi bắt buộc. |
| FR-22 | Phát hiện bất thường dựa trên rule đơn giản. |
| FR-23 | Cập nhật trạng thái realtime bằng SSE. |
| FR-25 | Prototype SDK Java, nếu còn thời gian. |
| FR-26 | Offline-first và xử lý batch nâng cao bằng PTB. |
| FR-30 | Anchor hash lô sổ cái lên Sui để chống sửa đổi lịch sử. |
| FR-31 | Vai trò STAFF (nhân viên merchant) với quyền hạn chế. |
| FR-32 | MCP tool ghi (ví dụ kích hoạt campaign), chỉ chạy sau bước xác nhận của người dùng. |
| FR-33 | Admin retry thủ công giao dịch FAILED. |

### 2.5. Won't, không thuộc MVP

Các hạng mục sau không làm trong MVP, có thể mô tả trong phần hướng phát triển (future work):

- FR-16: zkLogin và sponsored transaction (Enoki). Lý do: Signer tự trả gas (xem ADR-08).
- FR-21: Lưu bằng chứng kiểm toán lên Walrus và mã hóa bằng Seal. Lý do: dùng audit log PostgreSQL và anchor hash (FR-30).
- FR-24: ClickHouse cho analytics. Lý do: dùng truy vấn PostgreSQL (FR-19).
- Triển khai production trên Sui Mainnet.
- Cam kết uptime 99,9% trong môi trường production.
- Triển khai mTLS thực tế giữa các dịch vụ.
- Triển khai offline-first hoàn chỉnh.
- Phát hành SDK production cho các ngôn ngữ ngoài TypeScript. FR-25 vẫn chỉ là prototype tùy chọn.
- Triển khai module Move riêng cho từng tenant. Dùng một package và object cấu hình theo tenant.
- Stripe thật.

---

## 3. Yêu cầu phi chức năng

| Mã | Yêu cầu | Ưu tiên | Tiêu chí xác minh |
|---|---|---|---|
| NFR-01 | p95 dưới 100 ms cho issue, redeem và balance | Must | Kiểm thử API ngoại chuỗi với 20 virtual users và tối thiểu 1.000 request cho mỗi endpoint sau warm-up. p95 phải dưới 100 ms trong môi trường được mô tả trong báo cáo (CPU, RAM, chạy bằng docker compose cục bộ). Không tính thời gian settlement lên Sui. **Đo lần đầu ở Sprint 4** để kịp tối ưu. |
| NFR-02 | Bảo mật thông tin xác thực và dữ liệu | Must | Không commit private key, API secret hoặc file `.env` thật. API secret được mã hóa khi lưu trữ. JWT có RBAC và kiểm tra tenant ownership. Có rà soát bảo mật và secret scanning. |
| NFR-03 | Redeem đồng thời không làm số dư âm | Must | Test dùng PostgreSQL Testcontainers. Với số dư đầu kỳ 100 điểm và 20 request đồng thời, mỗi request redeem 10 điểm bằng key riêng: **đúng 10 request thành công, 10 request bị từ chối (422), số dư cuối bằng 0** và khớp tổng sổ cái. |
| NFR-04 | CI phải xanh trước khi merge | Must | GitHub Actions thực hiện build và test. Không merge khi các kiểm thử bắt buộc thất bại. |
| NFR-05 | Settlement có thể kiểm chứng | Must | Chụp bằng chứng đầu tiên (digest và ảnh Sui Explorer) **ngay khi settlement chạy được trên Testnet**, rồi cập nhật bằng bản mới nhất trong báo cáo kiểm thử tuần 7. Bằng chứng được dùng để nghiệm thu yêu cầu này kể cả khi Testnet gặp sự cố trong buổi demo trực tiếp. |
| NFR-06 | Giám sát cơ bản | Should | Spring Boot Actuator cung cấp health endpoint và metric cơ bản. Không cam kết uptime production. |

### 3.1. Sản phẩm tài liệu bắt buộc (theo yêu cầu môn học)

| Mã | Tài liệu | Ưu tiên | Mốc dự kiến (Sprint Jira) |
|---|---|---|---|
| D-01 | User Requirement Document (URD) | Must | Sprint 2–3 |
| D-02 | Software Requirement Specification (SRS) | Must | v1: Sprint 5; hoàn thiện: Sprint 7 |
| D-03 | Kiến trúc hệ thống và thiết kế chi tiết (gồm ADR) | Must | v1: Sprint 5; hoàn thiện: Sprint 7 |
| D-04 | UML 2.0 (use case, sequence, component, ERD) | Must | Sprint 3 |
| D-05 | Database schema và đặc tả Move contract | Must | Sprint 7 |
| D-06 | Hướng dẫn SDK và MCP | Must | Sprint 7 |
| D-07 | Test plan và báo cáo kiểm thử | Must | Test plan: Sprint 6; báo cáo: Sprint 8 |
| D-08 | Installation Guide (README) | Must | Sprint 7 |
| D-09 | User Manual (Merchant, Admin) | Must | Sprint 8 |
| D-10 | Tài liệu API (OpenAPI) | Must | Sprint 4 |

---

## 4. Kịch bản demo MVP, khoảng 10 phút

| Bước | Người thực hiện | Thao tác | Kết quả mong đợi | Yêu cầu |
|---|---|---|---|---|
| 1 | Merchant | Đăng ký tenant "Siêu thị A" | Tenant ở trạng thái PENDING. | FR-01 |
| 2 | Admin | Đăng nhập, duyệt tenant | Tenant chuyển sang APPROVED. | FR-02, FR-04 |
| 3 | Merchant | Tạo API key và cấu hình rule | Secret chỉ hiển thị một lần. Rule được lưu thành công. | FR-03, FR-08 |
| 4 | Developer | Chạy SDK, issue cho khách C1 với đơn hàng 100.000 VND | Cấp 100 điểm theo rule. API trả kết quả nghiệp vụ và settlement status (PENDING). | FR-05, FR-11 |
| 5a | Developer | Gửi lại request nghiệp vụ với cùng idempotency key (timestamp, nonce, chữ ký mới) | Trả về kết quả cũ, số dư không tăng thêm. | FR-05 |
| 5b | Developer | Gửi lại **đúng nguyên** request đã ký (cùng nonce) | HTTP 401, mã lỗi nonce đã dùng. Chứng minh replay khác idempotency. | FR-14 |
| 5c | Developer | Gửi request có chữ ký sai | HTTP 401. | FR-14 |
| 6 | Developer | Redeem 30 điểm, sau đó thử redeem 100 điểm khi số dư không đủ | Redeem đầu thành công, số dư còn 70. Request vượt số dư bị từ chối (422) và không tạo ledger entry thành công. | FR-06 |
| 7 | Merchant | Mở lịch sử và số dư trên Portal | Thấy đúng số dư, giao dịch issue/redeem, settlement status và digest nếu settlement đã hoàn tất. Lưu ý: chờ chu kỳ batch 5–10 giây và thời gian xác nhận, cần diễn tập để biết độ trễ thực tế. | FR-07, FR-12 |
| 8 | Cả nhóm | Mở transaction digest của batch đã settle trên Sui Explorer | Giao dịch thực tế được tìm thấy trên Testnet. Nếu Testnet gặp sự cố trong buổi demo, trình bày bằng chứng đã lưu trong báo cáo tuần 7 và nói rõ trạng thái demo trực tiếp. | FR-09, FR-10, NFR-05 |
| 9 | Admin | Mở dashboard và audit log | Thấy số tenant, số giao dịch, trạng thái settlement và log duyệt tenant/tạo key. | FR-13, FR-15 |
| 10 (Should) | Developer | Hỏi AI Agent số dư của khách C1 qua MCP | Agent trả đúng số dư 70 và chỉ truy cập được dữ liệu tenant của mình. | FR-17 |

---

## 5. Phương án dự phòng khi demo

- Nếu Sui Testnet chậm hoặc lỗi, hệ thống được phép chạy với `MockChainClient`.
- Chế độ chạy được thể hiện bằng trường riêng `settlementMode` (LIVE hoặc MOCK), độc lập với `settlementStatus`. Giao dịch ở chế độ MOCK phải hiển thị rõ nhãn MOCK trên Portal.
- Không được gán transaction digest cũ cho một giao dịch mock mới.
- Digest đã lưu từ trước chỉ được sử dụng như ví dụ tham khảo và phải được ghi rõ là giao dịch mẫu.
- Nếu mạng không ổn định, nhóm sử dụng video demo đã quay trước hoặc báo cáo kiểm thử kèm bằng chứng Explorer để chứng minh giao dịch Testnet đã thực hiện trước đó.
- Mỗi thành viên phụ trách một phần demo. Nhóm phải diễn tập toàn bộ kịch bản ít nhất hai lần.

---

## 6. Tiêu chí hoàn thành MVP

MVP được coi là hoàn thành khi đáp ứng tất cả điều kiện sau:

1. Toàn bộ yêu cầu Must (FR và NFR) đạt tiêu chí nghiệm thu.
2. Kịch bản demo từ bước 1 đến bước 9 chạy được theo tài liệu hướng dẫn, hoặc có bằng chứng thay thế đã được xác định cho bước xác minh Testnet khi mạng gặp sự cố.
3. Có thể khởi động các dịch vụ local bằng docker compose sau khi cấu hình biến môi trường theo README.
4. Move contract đã được triển khai trên Sui Testnet và package ID được ghi trong tài liệu cấu hình.
5. Báo cáo kiểm thử tuần 7 có ít nhất một giao dịch settlement thực tế được xác minh trên Sui Explorer, kèm digest và ảnh chụp bằng chứng.
6. Có kiểm thử cho issue, redeem, idempotency, HMAC (gồm replay), RBAC và cô lập tenant, redeem đồng thời, và retry settlement của worker.
7. GitHub Actions build và test thành công.
8. Không có secret hoặc private key thật trong repository.
9. README mô tả cách cài đặt, chạy hệ thống, cấu hình Testnet, chạy test và xử lý lỗi phổ biến.
10. Đủ tài liệu D-01 đến D-10 ở mục 3.1.

---

## 7. Quy tắc hạ ưu tiên khi trễ (thống nhất trước, áp dụng tại cổng Sprint 5)

Cổng cắt phạm vi: cuối Sprint 5 (3–7/11). Nếu Must chưa xong, áp dụng theo thứ tự, mỗi mục cần giảng viên đồng ý nếu ảnh hưởng đề cương:

| Thứ tự | Hạ ưu tiên | Cách vẫn đạt MVP |
|---|---|---|
| 1 | Cắt toàn bộ Could | Không ảnh hưởng MVP |
| 2 | Cắt Should theo thứ tự: FR-19, FR-27, FR-29, FR-28 | Không ảnh hưởng demo bước 1–9 |
| 3 | FR-12: cấu hình rule trên giao diện → chỉ qua API | Demo bước 3 dùng API hoặc Swagger cho phần rule |
| 4 | FR-13: Admin Portal rút gọn còn danh sách tenant, nút duyệt và bảng audit log | Demo bước 9 giữ số liệu tối thiểu |
| 5 | NFR-01: chuyển từ "đạt" sang "đo và báo cáo, kèm phân tích nếu không đạt" | Báo cáo ghi rõ môi trường và kết quả |

FR-17 (MCP) giữ ở Should cao vì tên đề tài có MCP. Cắt FR-17 chỉ khi đã cắt hết các mục trên.

---
