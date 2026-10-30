# Báo cáo SCRUM-16: Chuẩn bị môi trường Sui Testnet

## 1. Mục tiêu

Chuẩn bị môi trường chạy Sui CLI, tạo ví Testnet, nhận token faucet và xác minh package Move bằng `sui move build` và `sui move test`.

## 2. Phạm vi thực hiện

- Bổ sung Docker image cho Sui CLI tại `docker/sui/Dockerfile`.
- Bổ sung service `sui-cli` trong `docker-compose.yml`.
- Duy trì volume `sui-config` cho keystore và cấu hình ví.
- Ghi lại quy trình setup, tạo ví, faucet, build và test tại [sui-setup.md](sui-setup.md).

## 3. Kết quả xác minh

| Hạng mục | Kết quả |
|---|---|
| Sui CLI | `sui 1.83.0-16d2dc1e05ed` |
| Môi trường | `testnet` |
| Faucet | Ví nhận `3.00 SUI` Testnet |
| Move build | Thành công |
| Move test | 1 test pass, 0 fail |

Test đã chạy:

```text
hello_octapoint::basic_tests::test_basic_math
Test result: OK. Total tests: 1; passed: 1; failed: 0
```

## 4. Bảo mật

- Không ghi recovery phrase, private key hoặc keystore vào repository.
- Volume `sui-config` dùng để lưu cấu hình ví ngoài source code.
- Chỉ dùng ví cho Sui Testnet; không tái sử dụng cho Mainnet.

## 5. Acceptance criteria

- [x] Build được package Move mẫu.
- [x] Test Move chạy thành công.
- [x] Ví Testnet nhận được token faucet.
- [x] Có hướng dẫn để thành viên khác thực hiện lại.

## 6. Bước tiếp theo

Dùng môi trường đã chuẩn bị cho phần Move/TypeScript Signer: đọc outbox, phát transaction lên Sui Testnet và cập nhật settlement status `PENDING`, `SETTLED` hoặc `FAILED`.
