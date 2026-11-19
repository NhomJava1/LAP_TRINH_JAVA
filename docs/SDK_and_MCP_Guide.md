# OctaPoint SDK & MCP Integration Guide

Hướng dẫn tích hợp chuẩn cho **Merchant System** (TypeScript SDK, Java 25 SDK) và **AI Agents / LLM Assistants** (Model Context Protocol Server) theo đặc tả **ADR-0003**, `FR-11`, `FR-17`, `FR-25`, `FR-26`, `FR-28` và `FR-32`.

---

## 1. Giao thức Xác thực HMAC-SHA256 (`ADR-0003`)

Mọi request tới `/api/merchant/v1/**` bắt buộc có 4 header bảo mật:

| Header | Mô tả | Ghi chú |
|---|---|---|
| `X-OP-Key` | Mã API Key được cấp tại `/merchant/api-keys` (vd: `ak_...`) | Key bị revoke hoặc thuộc tenant chưa `APPROVED` sẽ bị từ chối |
| `X-OP-Timestamp` | Unix epoch seconds tại thời điểm ký | Hợp lệ trong cửa sổ `+/- 300` giây |
| `X-OP-Nonce` | Chuỗi duy nhất (UUID v4) cho mỗi request | Lưu trên Redis `TTL = 600s`; gửi lặp trả `401 AUTH_NONCE_REUSED` |
| `X-OP-Signature` | `HEX(HMAC_SHA256(apiSecret, stringToSign))` | So sánh constant-time phía Gateway |

Đối với các request ghi (`POST /issue`, `POST /redeem`), bắt buộc gửi thêm header:
- `Idempotency-Key`: Khóa duy nhất theo giao dịch nghiệp vụ ở phía POS/Merchant. Request lặp cùng `Idempotency-Key` và cùng body (nhưng timestamp + nonce mới) trả về kết quả cũ, không cộng/trừ điểm lần hai.

### Công thức `stringToSign`
```text
UPPER(METHOD) + "\n" +
PATH + "\n" +
CANONICAL_QUERY + "\n" +
TIMESTAMP + "\n" +
NONCE + "\n" +
LOWER_HEX(SHA256(RAW_BODY_BYTES))
```
- `CANONICAL_QUERY`: Tách cặp `key=value`, decode form encoding, encode chuẩn **RFC 3986** (`%20` cho dấu cách), sắp xếp tăng dần theo `(key, value)` và nối bằng `&`. Nếu không có query string thì là chuỗi rỗng `""`.
- **Test vector liên ngôn ngữ** (được assert đồng thời ở `HmacCodecTest.java`, `sdk-ts/test/hmac.test.ts`, `OctaPointJavaClientTest.java`):
  - Secret: `sec_shared_test_vector_2026`
  - Method / Path / Query: `POST` `/api/merchant/v1/credits/issue` `z=2&a=hello%20world`
  - Timestamp / Nonce: `1700000000` / `nonce-vector-001`
  - Body: `{"customerId":"C1","amountVnd":100000}`
  - Expected `X-OP-Signature`: `987d1a5ef200d35e8d2d849a7bdc15b1450d0f3c4bf284561a08c8e619d40e1d`

---

## 2. TypeScript / Node.js SDK (`sdk-ts/`)

### Cài đặt & Build
```bash
cd sdk-ts
npm install
npm test
npm run build
```

### Sử dụng `OctaPointClient`
```ts
import { OctaPointClient, OfflineCreditQueue, verifyWebhookSignature } from '@octapoint/sdk';

const client = new OctaPointClient({
  baseUrl: 'http://localhost:8080',
  apiKey: process.env.OCTAPOINT_API_KEY!,
  apiSecret: process.env.OCTAPOINT_API_SECRET!,
});

// 1. Cấp điểm từ hóa đơn 100.000 VND (FR-05)
const issued = await client.issue({
  customerId: 'cust_123',
  orderId: 'ORD-2026-001',
  amountVnd: 100000,
});

// 2. Đổi 30 điểm (FR-06)
const redeemed = await client.redeem({
  customerId: 'cust_123',
  orderId: 'RED-2026-001',
  pointsToRedeem: 30,
});

// 3. Tra cứu số dư, lịch sử, rule và thống kê (FR-07, FR-17)
const bal = await client.balance('cust_123');
const hist = await client.history('cust_123');
const rules = await client.rules();
const summary = await client.summary();
```

### Hàng đợi Offline-First cho POS (`OfflineCreditQueue` — `FR-26`)
Khi cửa hàng mất kết nối tạm thời, POS lưu thao tác kèm `idempotencyKey` cố định và flush lại theo FIFO khi có mạng:
```ts
const queue = new OfflineCreditQueue();
queue.enqueueIssue({ customerId: 'cust_123', orderId: 'OFF-001', amountVnd: 50000 });

// Khi khôi phục mạng:
const report = await queue.flush(client);
console.log(report); // { processed, remaining, errors }
```

### Xác thực Webhook Settlement (`verifyWebhookSignature` — `FR-28`)
OctaPoint ký webhook qua header `X-OP-Webhook-Timestamp` và `X-OP-Webhook-Signature` bằng chuỗi `${timestamp}.${rawBodyJson}`:
```ts
const isValid = verifyWebhookSignature({
  webhookSecret: process.env.OCTAPOINT_WEBHOOK_SECRET!,
  timestamp: req.header('X-OP-Webhook-Timestamp') ?? '',
  rawBody: rawBodyBuffer,
  signature: req.header('X-OP-Webhook-Signature') ?? '',
  toleranceSeconds: 300,
});
```

---

## 3. Java 25 SDK (`sdk-java/` — `FR-25`)

SDK thuần Java 25 dùng `java.net.http.HttpClient` và Jackson (`pom.xml` trong `sdk-java/`).

```java
import com.octapoint.sdk.HmacSigner;
import com.octapoint.sdk.OctaPointJavaClient;
import java.util.Map;

OctaPointJavaClient client = new OctaPointJavaClient(
        "http://localhost:8080",
        System.getenv("OCTAPOINT_API_KEY"),
        System.getenv("OCTAPOINT_API_SECRET")
);

Map<String, Object> issueRes = client.issue("cust_123", "ORD-JAVA-1", 100_000L, "idem-java-001");
Map<String, Object> balance = client.balance("cust_123");
Map<String, Object> rules = client.rules();

// Xác thực chữ ký Webhook phía backend Java của Merchant:
boolean validWebhook = HmacSigner.verifyWebhookSignature(
        webhookSecret, timestampHeader, rawBody, signatureHeader, 300, Instant.now().getEpochSecond());
```

Chạy kiểm thử Java SDK:
```powershell
docker run --rm -v octapoint-m2:/root/.m2 -v "${PWD}/sdk-java:/workspace" -w /workspace maven:3.9-eclipse-temurin-25 mvn --batch-mode test
```

---

## 4. OctaPoint MCP Server cho AI Agent (`mcp-server/` — `FR-17`, `FR-32`)

### Nguyên tắc An toàn AI
1. **Default Read-Only (`FR-17`)**: Mặc định chỉ đăng ký 4 công cụ đọc và khuyên dùng API Key có scope `READ`. Khi AI thử ghi bằng key `READ`, Gateway chặn cứng `HTTP 403 INSUFFICIENT_SCOPE`.
2. **Human-in-the-Loop Write (`FR-32`)**: Hai công cụ ghi (`prepare_bonus_grant` và `confirm_bonus_grant`) chỉ được mở khi đặt `MCP_WRITE_TOOLS_ENABLED=true`, và bắt buộc người dùng xác nhận thủ công qua `confirmationToken` dùng một lần.
3. Không bao giờ trả `apiSecret` trong output hoặc error log của MCP tool.

### Danh sách MCP Tools
| Tool | Chế độ | Mô tả |
|---|---|---|
| `get_customer_balance` | Read (`FR-17`) | Đọc số dư, điểm tích lũy, hạng (`tier`) và trạng thái settlement |
| `list_customer_transactions` | Read (`FR-17`) | Liệt kê lịch sử `ISSUE` / `REDEEM` kèm `txDigest` |
| `get_tenant_rules` | Read (`FR-17`) | Xem quy tắc earn/redeem, hạn điểm và hệ số Silver/Gold |
| `get_tenant_summary` | Read (`FR-17`) | Xem tổng điểm lưu hành và thống kê Outbox |
| `prepare_bonus_grant` | Write Step 1 (`FR-32`) | Tạo đề xuất thưởng điểm ở trạng thái `AWAITING_HUMAN_CONFIRMATION` (TTL 5 phút) |
| `confirm_bonus_grant` | Write Step 2 (`FR-32`) | Chỉ thực thi `client.issue` khi `humanApproved = true` và token chưa dùng |

### Cấu hình Stdio MCP Client (Claude Desktop / Cursor)
```json
{
  "mcpServers": {
    "octapoint": {
      "command": "node",
      "args": ["D:/Y3/java/project/LAP_TRINH_JAVA/mcp-server/dist/index.js"],
      "env": {
        "OCTAPOINT_BASE_URL": "http://localhost:8080",
        "OCTAPOINT_API_KEY": "ak_read_only_key",
        "OCTAPOINT_API_SECRET": "sec_...",
        "MCP_WRITE_TOOLS_ENABLED": "false"
      }
    }
  }
}
```

---

## 5. Bảng Mã Lỗi Ổn định (`GlobalExceptionHandler` & Security Filters)

| HTTP | Mã lỗi (`error`) | Ý nghĩa & Hướng xử lý |
|---|---|---|
| `400` | `VALIDATION_ERROR`, `AMOUNT_TOO_SMALL`, `INVALID_RULE_RATE` | Payload không hợp lệ hoặc số tiền nhỏ hơn mức tối thiểu `1` điểm |
| `401` | `AUTH_MISSING_HEADERS`, `AUTH_INVALID_TIMESTAMP`, `AUTH_NONCE_REUSED`, `AUTH_INVALID_SIGNATURE`, `AUTH_INVALID_KEY` | Thiếu header `X-OP-*`, đồng hồ lệch quá `300s`, phát lại nonce trùng hoặc sai chữ ký HMAC |
| `403` | `INSUFFICIENT_SCOPE`, `TENANT_NOT_APPROVED`, `FORBIDDEN_MERCHANT_OWNER_ONLY`, `FORBIDDEN_ADMIN_ONLY` | API key thiếu quyền (`READ` gọi `/issue`), tenant chưa `APPROVED` hoặc `STAFF` gọi API chỉ dành cho owner |
| `409` | `IDEMPOTENCY_CONFLICT`, `EMAIL_ALREADY_EXISTS` | Tái sử dụng `Idempotency-Key` với payload khác hoàn toàn giao dịch gốc |
| `422` | `INSUFFICIENT_BALANCE` | Số dư điểm của khách hàng nhỏ hơn `pointsToRedeem` |
| `429` | `RATE_LIMITED` | Vượt ngưỡng `RATE_LIMIT_CAPACITY` (mặc định `120` request/phút theo key hoặc IP) |
