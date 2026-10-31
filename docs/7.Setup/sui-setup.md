# Hướng dẫn setup Sui CLI (Docker, Testnet)

| Mục | Nội dung |
|---|---|
| Ticket | SCRUM-36 (parent SCRUM-16) |
| Mục đích | Chuẩn bị môi trường cho phần Signer / Move của OctaPoint |
| Truy vết | Task hạ tầng, không gắn FR/UC cụ thể |
| Phạm vi | Cài CLI, ví Testnet, faucet, `sui move build/test` trên package mẫu |
| Ngoài phạm vi | Contract OctaPoint, Signer, sponsored tx (đã bỏ, Signer tự trả gas) |

> ⚠️ Các lệnh dưới đây là lệnh `sui` phổ biến. Tên image Docker, flag và phiên bản
> **cần kiểm tra trong tài liệu chính thức của Sui** (docs.sui.io) vì có thể đổi theo version.
> Mục có `<...>` là chỗ người thực hiện phải điền.

## 0. Yêu cầu
- Docker đã cài và chạy (`docker --version`).
- Kết nối Internet (để kéo image, gọi Testnet RPC, xin faucet).
- **`git` có trong container** (Sui tải thư viện Move qua `git clone`). Đã thêm vào `docker/sui/Dockerfile`.
- Phiên bản đã thử: `sui 1.83.0-16d2dc1e05ed`.

## 1. Build image và chạy Sui CLI trong Docker

> Lệnh chạy trên **máy host** (PowerShell ngoài container).
> **Dán từng lệnh một**, tránh dán cả khối.

```powershell
# Build image từ Dockerfile có sẵn trong repo
docker compose build sui-cli

# Chạy container tương tác, mount ví và workspace
docker run -it --rm `
  -v octapoint-sui-config:/root/.sui `
  -v "${PWD}:/workspace" `
  -w /workspace `
  lap_trinh_java-sui-cli:latest bash
```

| Volume | Vai trò |
|---|---|
| `octapoint-sui-config` | Giữ keystore và cấu hình ví (`~/.sui`) bền vững qua các container |
| `${PWD}` → `/workspace` | Mã nguồn Move nằm trên máy host |

Kiểm tra bên trong container:
```bash
sui --version
# Kết quả đã thử: sui 1.83.0-16d2dc1e05ed
```

## 2. Kết nối Testnet và tạo ví

```bash
sui client envs                         # xem các môi trường
sui client switch --env testnet         # chuyển sang testnet
sui client active-env                   # phải trả về: testnet
sui client new-address ed25519          # tạo địa chỉ mới (chỉ chạy 1 lần)
sui client active-address               # xem địa chỉ active
```

Lần đầu chạy `sui client`, CLI hỏi tạo config — chọn Testnet và lưu keystore vào volume.

Kết quả đã thử: `active-env = testnet`; ví active `0xcf65…8996` (rút gọn).
**Không ghi recovery phrase vào docs/log.**

## 3. Xin token từ faucet

```bash
sui client faucet
sui client gas        # kiểm tra số dư
```

- Nếu faucet báo rate limit: dùng web faucet chính thức của Sui và dán địa chỉ ví.

Kết quả đã thử:
```
╭─────────────────┬────────────────────┬──────────────────╮
│ gasCoinId       │ mistBalance (MIST) │ suiBalance (SUI) │
├─────────────────┼────────────────────┼──────────────────┤
│ address balance │ 3000000000         │ 3.00             │
╰─────────────────┴────────────────────┴──────────────────╯
```

## 4. Tạo và chạy package Move mẫu

```bash
sui move new hello_octapoint
cd hello_octapoint
```

### 4.1 File test mẫu

Tạo `tests/basic_tests.move`:

```move
#[test_only]
module hello_octapoint::basic_tests;

#[test]
fun test_basic_math() {
    assert!(1u64 + 1u64 == 2u64, 0);
}
```

### 4.2 Build và test

```bash
sui move build
```
Kết quả:
```
INCLUDING DEPENDENCY MoveStdlib
INCLUDING DEPENDENCY Sui
BUILDING hello_octapoint
```

```bash
sui move test
```
Kết quả:
```
INCLUDING DEPENDENCY MoveStdlib
INCLUDING DEPENDENCY Sui
BUILDING hello_octapoint
Running Move unit tests
[ PASS    ] hello_octapoint::basic_tests::test_basic_math
Test result: OK. Total tests: 1; passed: 1; failed: 0
```

## 5. Checklist xác nhận (Acceptance)

| # | Tiêu chí | Cách chứng minh | Đạt? |
|---|---|---|---|
| 1 | Chạy được build và test | Output mục 4 — 1 test PASS | ✅ |
| 2 | Ví có token Testnet | Output `sui client gas` mục 3 — 3.00 SUI | ✅ |
| 3 | Người khác làm lại được | 1 người trong nhóm làm theo docs từ đầu | ☐ |

## 6. Lỗi thường gặp

| Triệu chứng | Nguyên nhân | Cách xử lý |
|---|---|---|
| Mất ví sau khi thoát container | Chưa mount volume | Dùng `-v octapoint-sui-config:/root/.sui` như mục 1 |
| Faucet báo rate limit | Xin quá nhiều lần | Đợi rồi thử lại hoặc dùng web faucet |
| `sui move build` lỗi `git clone` | Container thiếu `git` | Đã fix trong `docker/sui/Dockerfile` — thêm `git` vào `apt-get install` |
| Build lỗi tải dependency dù có git | Mạng Docker bị chặn GitHub | Kiểm tra mạng/proxy Docker |
| `TLS handshake timeout` khi `docker build` | Docker Engine mất kết nối Docker Hub | Thêm DNS `8.8.8.8`, `1.1.1.1` trong Docker Desktop Settings |
| Faucet có coin nhưng ví mới trống | `new-address` không đổi ví active | `sui client switch --address <addr>` |

## 7. Bảo mật

- **Không commit** keystore, private key, recovery phrase vào repo.
- **Không dán `recoveryPhrase` vào ticket/docs/log.** Cắt trước khi chụp hoặc copy.
- Chỉ dùng ví này cho Testnet. Không dùng lại khóa Mainnet.
- Đã thêm vào `.gitignore`: `*.keystore`, `.sui/`.

## 8. Bước kế tiếp

Dùng môi trường này cho task Move/Signer tiếp theo của SCRUM-16:
- Xây dựng TypeScript Signer đọc outbox từ PostgreSQL và phát transaction lên Sui Testnet.
- Quản lý settlement status (`PENDING` → `SETTLED` / `FAILED`) độc lập với nghiệp vụ (issue/redeem).
