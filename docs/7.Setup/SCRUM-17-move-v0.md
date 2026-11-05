# SCRUM-17: Move v0 — Issue và Balance

## Trạng thái

Đã hoàn thành trên Sui Testnet.

| Tiêu chí | Kết quả |
|---|---|
| Move build | Pass |
| Move unit test | Pass — 1 test, 0 failed |
| Publish Testnet | Pass |
| Gọi `issue` | Pass |
| Xác nhận balance | Pass |

## Package Testnet

| Hạng mục | Giá trị |
|---|---|
| Network | `testnet` |
| Sender | `0xcf651235dad1649647b3b39563e1bebbc16f27e5861ea96544d3dacf3d928996` |
| Package ID | `0x908b5905ece899ac50024a2d33d96cd2465608b44a338689d778a6480fd5c17b` |
| Publish digest | `7e8a57oRUKNVvnJYuuGwM5bEdhRKrDpNsuksRBta6FRx` |
| AdminCap ID | `0xab12c867037151d10d00ee14acd114fbf0ee6d4488cab42f936c1fbcc2834fbc` |
| Balance ID | `0x76d11e3a5a2ed8d3b78aa7592ba6fe1902d094be4ee0119ed0c7ea051158c0c7` |

## Object model

### `AdminCap`

- Owned object.
- Được tạo trong `init`.
- Chuyển cho sender khi package publish.
- Là capability bắt buộc để gọi `issue`.
- Object ID: `0xab12c867037151d10d00ee14acd114fbf0ee6d4488cab42f936c1fbcc2834fbc`.

### `Balance`

- Shared object.
- Được tạo trong `init`.
- Lưu trường `amount: u64`.
- Được cập nhật bởi `issue`.
- Object ID: `0x76d11e3a5a2ed8d3b78aa7592ba6fe1902d094be4ee0119ed0c7ea051158c0c7`.

## Move functions

### `issue`

```text
issue(&AdminCap, &mut Balance, amount)
```

Tăng `Balance.amount` thêm `amount`. Caller phải sở hữu `AdminCap`.

### `balance`

```text
balance(&Balance): u64
```

Đọc giá trị `Balance.amount`.

## Unit test

Test gọi `issue` hai lần với `100` và `25`, sau đó xác nhận:

```text
Balance.amount == 125
```

Kết quả:

```text
[ PASS ] octapoint_move::octapoint_tests::test_issue_increases_balance
Test result: OK. Total tests: 1; passed: 1; failed: 0
```

## Testnet verification

Lệnh publish thành công:

```bash
sui client publish --gas-budget 100000000
```

Lệnh issue lần gần nhất:

```bash
sui client call \
  --package 0x908b5905ece899ac50024a2d33d96cd2465608b44a338689d778a6480fd5c17b \
  --module octapoint \
  --function issue \
  --args 0xab12c867037151d10d00ee14acd114fbf0ee6d4488cab42f936c1fbcc2834fbc 0x76d11e3a5a2ed8d3b78aa7592ba6fe1902d094be4ee0119ed0c7ea051158c0c7 100 \
  --gas-budget 10000000
```

| Hạng mục | Giá trị |
|---|---|
| Issue digest gần nhất | `Hrkssbqoi9HJnDPA56LrRPH1S4RMWYBx47iRKUURjYvj` |
| Issue status | `Success` |
| Balance version sau issue | `4` |
| Balance object digest | `B8qjecGcu2v9Rza84HCSsVPZouHMDeUsfghALBkYUYin` |
| Balance hiện tại | `225` |

`amount = 225` là giá trị cộng dồn sau các lần issue trước đó; không phải lỗi publish hay lỗi transaction.

Kiểm tra bằng:

```bash
sui client object 0x76d11e3a5a2ed8d3b78aa7592ba6fe1902d094be4ee0119ed0c7ea051158c0c7
```

## Source files

- `contracts/octapoint_move/Move.toml`
- `contracts/octapoint_move/sources/octapoint.move`
- `contracts/octapoint_move/tests/octapoint_tests.move`
