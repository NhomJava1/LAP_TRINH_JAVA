# OctaPoint: User Requirement Document (URD)

| **Phiên bản** | 0.1 (khung và nội dung nền) |
|---|---|
| **Ticket** | SCRUM-13 |
| **Người phụ trách** | Minh Hoang |
| **Căn cứ** | `requirement-index-v0.2.md`, `usecase-v1.md`, đề tài ban đầu |
| **Trạng thái** | Dự thảo, chờ nhóm review |

URD mô tả **người dùng cần gì**, bằng ngôn ngữ của người dùng. Cách hệ thống thực hiện (kỹ thuật, API, cơ sở dữ liệu) được đặc tả trong SRS và tài liệu kiến trúc.

---

## Mục lục

1. Giới thiệu
2. Tổng quan sản phẩm
3. Người dùng và các bên liên quan
4. Phạm vi theo MoSCoW
5. Yêu cầu người dùng (UR)
6. Yêu cầu chất lượng ở mức người dùng
7. Quy tắc nghiệp vụ
8. Ràng buộc, giả định và rủi ro
9. UR, FR và Use case
10. Phụ lục: thuật ngữ 

---

## 1. Giới thiệu

### 1.1. Mục đích

Tài liệu ghi lại các yêu cầu của người dùng đối với OctaPoint để nhóm phát triển, giảng viên cùng hiểu một cách thống nhất sản phẩm sẽ làm gì và không làm gì.

### 1.2. Phạm vi sản phẩm

OctaPoint là nền tảng **Credit-as-a-Service**: cho phép doanh nghiệp (siêu thị, trung tâm thương mại, chuỗi F&B...) vận hành hệ thống tín dụng và điểm thưởng an toàn mà không phải tự xây từ đầu. Doanh nghiệp tích hợp qua SDK TypeScript, còn lịch sử giao dịch được xác nhận bất biến trên blockchain Sui (Testnet).

### 1.3. Đối tượng đọc

| Đối tượng đọc | Mục tiêu |
|---|---|
| Nhóm phát triển | Hiểu yêu cầu, làm cơ sở viết SRS |
| Giảng viên | Đánh giá phạm vi và mức độ hoàn thành |
| Người kiểm thử | Xây dựng kịch bản nghiệm thu |

### 1.4. Tài liệu tham khảo

| Tài liệu | Vai trò |
|---|---|
| `requirement-index-v0.2.md` | Mã FR, NFR, MoSCoW, kịch bản demo |
| `usecase-v1.md` | Actor và use case |
| Đề xuất đề tài ban đầu | Bối cảnh và mục tiêu gốc |

---

## 2. Tổng quan sản phẩm

### 2.1. Bối cảnh và vấn đề

Hệ thống tích điểm và tín dụng hiện nay thường gặp bốn vấn đề:

| # | Vấn đề | Hậu quả |
|---|---|---|
| 1 | Cơ sở dữ liệu điểm tập trung dễ bị spam API, brute-force, phát hành điểm trái phép và gian lận nội bộ | Thất thoát điểm, mất niềm tin |
| 2 | Doanh nghiệp khó tự xây engine tín dụng an toàn và minh bạch | Triển khai chậm, chi phí bảo trì cao |
| 3 | Đối soát thiếu minh bạch giữa khách hàng, doanh nghiệp và đối tác | Tranh chấp số liệu |
| 4 | Giải pháp blockchain hiện có bắt người dùng quản lý ví, seed phrase và phí gas | Rào cản triển khai lớn |

### 2.2. Giải pháp đề xuất

OctaPoint che giấu độ phức tạp của blockchain: doanh nghiệp chỉ cần gọi SDK như một dịch vụ web thông thường. Số dư và giao dịch được xử lý nhanh trong cơ sở dữ liệu của nền tảng, sau đó được xác nhận bất đồng bộ lên Sui để có bằng chứng không thể sửa đổi. Nền tảng tự trả phí gas, người dùng không cần ví.

### 2.3. Mục tiêu

| # | Mục tiêu | Cách đo |
|---|---|---|
| G1 | Merchant cộng và trừ điểm an toàn qua SDK | Demo thành công |
| G2 | Chống gian lận cơ bản: giả mạo request, phát lại, trừ quá số dư | Bộ test bảo mật (NFR-02, NFR-03) |
| G3 | Mọi giao dịch có bằng chứng kiểm chứng được trên blockchain | Digest xem được trên Sui Explorer (NFR-05) |
| G4 | Thao tác nhanh, không làm chậm quầy thanh toán | p95 dưới 100 ms (NFR-01) |
| G5 | Dễ tích hợp cho nhà phát triển | SDK TypeScript và tài liệu hướng dẫn |

---

## 3. Người dùng và các bên liên quan

### 3.1. Người dùng (actor)

| Actor | Nhu cầu chính |
|---|---|
| Merchant | Đăng ký, quản lý API key, cấu hình quy tắc điểm, theo dõi số dư và lịch sử |
| Merchant System (POS, e-commerce qua SDK) | Cộng và trừ điểm tự động, tra cứu số dư nhanh, không bị cộng trùng |
| System Admin | Duyệt doanh nghiệp, giám sát hệ thống, xem nhật ký thao tác |
| AI Agent (Should) | Truy vấn dữ liệu của merchant qua MCP trong phạm vi được phép |
| Sui Blockchain | Hệ thống ngoài nhận xác nhận giao dịch |

End-user (khách tích điểm) không tương tác trực tiếp với hệ thống trong MVP, họ được đại diện bằng mã khách hàng do hệ thống của merchant cung cấp.

### 3.2. Các bên liên quan

| Bên liên quan | Quan tâm |
|---|---|
| Nhóm phát triển | Hoàn thành đúng hạn, đúng phạm vi |
| Giảng viên | Tuân thủ yêu cầu môn học |

---

## 4. Phạm vi theo MoSCoW

| Mức | Nội dung chính | Yêu cầu liên quan |
|---|---|---|
| **Must (MVP)** | Đăng ký và duyệt tenant, API key, đăng nhập phân quyền, issue và redeem điểm, tra cứu số dư và lịch sử, rule cơ bản, settlement lên Sui, SDK TypeScript, Merchant Portal, Admin Portal, bảo mật API (HMAC, chống replay, rate limit), audit log | FR-01 đến FR-15, NFR-01 đến NFR-05 |
| **Should** | MCP Server cho AI Agent, rule nâng cao, analytics, webhook, đối soát, hạn mức gas | FR-17, FR-18, FR-19, FR-27, FR-28, FR-29 |
| **Could** | Hóa đơn giả lập, phát hiện bất thường, realtime SSE, prototype SDK Java, offline-first, anchor hash, vai trò nhân viên, MCP tool ghi, retry thủ công | FR-20, 22, 23, 25, 26, 30, 31, 32, 33 |
| **Won't** | zkLogin và sponsored transaction (Enoki), Walrus, ClickHouse, Mainnet, uptime 99,9%, mTLS, SDK production ngoài TypeScript, module Move riêng cho từng tenant, Stripe thật | Hướng phát triển |

---

## 5. Yêu cầu người dùng (UR)

Mỗi yêu cầu được viết theo dạng "người dùng cần...". Cột cuối liên kết sang yêu cầu chức năng (FR) và use case (UC).

### 5.1. Merchant

| Mã | Yêu cầu | Mức | FR, UC |
|---|---|---|---|
| UR-01 | Merchant cần tự đăng ký và chờ quản trị viên duyệt trước khi dùng dịch vụ | Must | FR-01; UC-01 |
| UR-02 | Merchant cần biết tình trạng đăng ký của mình (chờ duyệt, đã duyệt, bị từ chối, bị khóa) | Must | FR-12; UC-03 |
| UR-03 | Merchant cần tạo, thay mới và thu hồi khóa tích hợp (API key) để kiểm soát ai được gọi hệ thống, và secret chỉ hiển thị một lần | Must | FR-03; UC-05, 06, 07 |
| UR-04 | Merchant cần đặt quy tắc tích điểm và quy đổi điểm phù hợp chính sách của mình | Must | FR-08; UC-08 |
| UR-05 | Merchant cần xem số dư, lịch sử giao dịch của khách hàng và biết giao dịch đã được xác nhận trên blockchain chưa | Must | FR-07, FR-12; UC-11, 12 |
| UR-06 | Merchant cần đăng nhập an toàn và chỉ thấy dữ liệu của doanh nghiệp mình | Must | FR-04; UC-02 |

### 5.2. Hệ thống của merchant (POS, e-commerce)

| Mã | Yêu cầu | Mức | FR, UC |
|---|---|---|---|
| UR-07 | Hệ thống cần cộng điểm cho khách theo giá trị đơn hàng, và khi gửi lại cùng một yêu cầu do lỗi mạng thì không bị cộng hai lần | Must | FR-05; UC-09, 20 |
| UR-08 | Hệ thống cần trừ điểm khi khách dùng điểm, và không bao giờ trừ quá số dư kể cả khi nhiều giao dịch xảy ra cùng lúc | Must | FR-06; UC-10 |
| UR-09 | Hệ thống cần tra cứu số dư và lịch sử nhanh để không làm chậm quầy thanh toán | Must | FR-07, NFR-01; UC-11, 12 |
| UR-10 | Nhà phát triển cần một SDK dễ dùng, tự xử lý việc ký yêu cầu, để không phải tự lập trình phần bảo mật | Must | FR-11; UC-09 đến UC-12 |
| UR-11 | Merchant cần API từ chối yêu cầu giả mạo, bị phát lại hoặc gửi quá nhiều | Must | FR-14; UC-13 |
| UR-12 | Hệ thống cần được thông báo khi giao dịch đã xác nhận trên blockchain | Should | FR-28; UC-28 |

### 5.3. System Admin

| Mã | Yêu cầu | Mức | FR, UC |
|---|---|---|---|
| UR-13 | Admin cần duyệt, từ chối hoặc khóa doanh nghiệp | Must | FR-02; UC-04 |
| UR-14 | Admin cần xem tổng quan số doanh nghiệp, số giao dịch, tình trạng xác nhận blockchain và các giao dịch bị lỗi | Must | FR-13; UC-17, 19 |
| UR-15 | Admin cần xem nhật ký các thao tác nhạy cảm để truy vết khi có sự cố | Must | FR-15; UC-18 |
| UR-16 | Admin cần kiểm soát chi phí gas của từng doanh nghiệp | Should | FR-27; UC-27 |

### 5.4. Yêu cầu chung của nền tảng

| Mã | Yêu cầu | Mức | FR, NFR |
|---|---|---|---|
| UR-17 | Mọi giao dịch điểm cần có bằng chứng không thể sửa đổi trên blockchain để giải quyết tranh chấp | Must | FR-09, FR-10, NFR-05 |
| UR-18 | Số điểm của khách không được mất hoặc âm trong mọi tình huống, kể cả khi hệ thống lỗi giữa chừng | Must | NFR-03; FR-06, FR-09 |
| UR-19 | Doanh nghiệp cần cấu hình hạn dùng điểm, hạng thành viên và hệ số nhân điểm | Should | FR-18; UC-25 |
| UR-20 | Doanh nghiệp cần thống kê điểm phát ra, điểm đã đổi và mức hoạt động | Should | FR-19; UC-26 |
| UR-21 | Trợ lý AI cần hỏi số dư, lịch sử và quy tắc điểm của một merchant mà không xem được dữ liệu merchant khác | Should | FR-17; UC-22, 23, 24 |
| UR-22 | Quản trị viên cần kiểm tra số liệu trong hệ thống khớp với dữ liệu trên blockchain | Should | FR-29; UC-29 |

---

## 6. Yêu cầu chất lượng ở mức người dùng

| Mã | Chất lượng | Người dùng kỳ vọng | NFR |
|---|---|---|---|
| Q-01 | Tốc độ | Cộng, trừ điểm và tra cứu xong dưới 100 ms ở phần lớn yêu cầu (p95), không tính thời gian xác nhận blockchain | NFR-01 |
| Q-02 | Bảo mật | Khóa và mật khẩu không bị lộ. Doanh nghiệp này không xem được dữ liệu doanh nghiệp khác | NFR-02 |
| Q-03 | Toàn vẹn | Không âm điểm khi nhiều giao dịch đồng thời | NFR-03 |
| Q-04 | Chất lượng mã nguồn | Mã chỉ được hợp nhất khi kiểm thử tự động đạt | NFR-04 |
| Q-05 | Kiểm chứng được | Có thể chứng minh giao dịch tồn tại trên Sui Testnet | NFR-05 |
| Q-06 | Giám sát | Xem được tình trạng hoạt động cơ bản của hệ thống | NFR-06 |
| Q-07 | Dễ cài đặt | Chạy toàn bộ hệ thống trên máy cá nhân bằng một lệnh theo hướng dẫn | Mục 6, tiêu chí 3 của requirement index |

---

## 7. Quy tắc nghiệp vụ

| Mã | Quy tắc |
|---|---|
| BR-01 | Điểm là số nguyên không âm. Điểm cấp được làm tròn xuống |
| BR-02 | Tenant chỉ được cộng hoặc trừ điểm khi ở trạng thái APPROVED |
| BR-03 | Mỗi lần cộng hoặc trừ điểm phải kèm mã yêu cầu duy nhất (Idempotency-Key). Gửi lại cùng mã và cùng nội dung thì trả kết quả cũ, cùng mã khác nội dung thì bị từ chối |
| BR-04 | Số dư không bao giờ âm. Yêu cầu trừ quá số dư bị từ chối và không ghi nhận giao dịch |
| BR-05 | Sổ cái chỉ ghi thêm, không sửa hoặc xóa. Việc đính chính được thực hiện bằng giao dịch mới |
| BR-06 | Mỗi tenant có quy tắc điểm riêng. Quy tắc mới chỉ áp dụng cho giao dịch sau thời điểm cập nhật |
| BR-07 | Merchant chỉ truy cập dữ liệu thuộc tenant của mình |
| BR-08 | Giao dịch chỉ được đánh dấu SETTLED sau khi xác minh được trên Sui và lưu mã giao dịch (digest) |
| BR-09 | Secret của API key chỉ hiển thị một lần và được lưu ở dạng mã hóa |
| BR-10 | Giá trị mặc định trong demo: 1.000 VND = 1 điểm, 1 điểm = 10 VND. Đây là cấu hình, không cố định |

---

## 8. UR, FR và Use case

| UR | FR / NFR | Use case |
|---|---|---|
| UR-01 | FR-01 | UC-01 |
| UR-02 | FR-12 | UC-03 |
| UR-03 | FR-03 | UC-05, 06, 07 |
| UR-04 | FR-08 | UC-08 |
| UR-05 | FR-07, FR-12 | UC-11, 12 |
| UR-06 | FR-04 | UC-02 |
| UR-07 | FR-05 | UC-09, 14, 20 |
| UR-08 | FR-06 | UC-10, 14 |
| UR-09 | FR-07, NFR-01 | UC-11, 12 |
| UR-10 | FR-11 | UC-09 đến UC-12 |
| UR-11 | FR-14 | UC-13 |
| UR-12 | FR-28 | UC-28 |
| UR-13 | FR-02 | UC-04 |
| UR-14 | FR-13 | UC-17, 19 |
| UR-15 | FR-15 | UC-15, 18 |
| UR-16 | FR-27 | UC-27 |
| UR-17 | FR-09, FR-10, NFR-05 | UC-16, 21 |
| UR-18 | NFR-03, FR-06, FR-09 | UC-10, 16 |
| UR-19 | FR-18 | UC-25 |
| UR-20 | FR-19 | UC-26 |
| UR-21 | FR-17 | UC-22, 23, 24 |
| UR-22 | FR-29 | UC-29 |

Mọi yêu cầu Must (FR-01 đến FR-15) có ít nhất một UR tương ứng.

---

## 9. Phụ lục

| Thuật ngữ | Giải thích |
|---|---|
| Tenant | Một doanh nghiệp (merchant) sử dụng nền tảng, dữ liệu tách biệt với tenant khác |
| Credit, điểm | Đơn vị điểm thưởng hoặc tín dụng của khách hàng |
| Issue, redeem | Cấp điểm, quy đổi (trừ) điểm |
| Ledger (sổ cái) | Bảng ghi mọi giao dịch điểm, chỉ ghi thêm |
| Settlement | Việc xác nhận giao dịch lên blockchain |
| Outbox | Hàng chờ các sự kiện cần gửi lên blockchain, ghi cùng lúc với giao dịch |
| Idempotency-Key | Mã duy nhất để một thao tác gửi lại nhiều lần chỉ có tác dụng một lần |
| HMAC | Cách ký yêu cầu bằng khóa bí mật để chứng minh yêu cầu hợp lệ |
| Replay | Kẻ gian gửi lại một yêu cầu hợp lệ đã bị chặn bắt |
| SDK | Bộ thư viện giúp lập trình viên gọi dịch vụ dễ dàng |
| MCP | Giao thức để trợ lý AI truy vấn dữ liệu và công cụ |
| Sui, Move | Blockchain Sui và ngôn ngữ viết hợp đồng thông minh của nó |
| Testnet | Mạng thử nghiệm của Sui, không có giá trị tiền thật |
| Digest | Mã nhận dạng một giao dịch trên Sui, dùng để tra cứu trên Explorer |
