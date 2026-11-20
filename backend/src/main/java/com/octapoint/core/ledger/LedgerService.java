package com.octapoint.core.ledger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
public class LedgerService {

    // Ví dụ mẫu logic xử lý Issue/Redeem với Idempotency và Lock đồng thời
    @Transactional
    public synchronized void processLedgerTransaction(String customerId, String tenantId, String type, BigDecimal amount, String idempotencyKey, String requestHash) {
        // 1. Kiểm tra Idempotency Key logic ở đây
        // Nếu trùng key nhưng khác requestHash -> Ném ngoại lệ 409 Conflict
        // Nếu trùng key và trùng requestHash -> Trả về kết quả cũ đã lưu
        // 2. Lấy thông tin Customer kèm Pessimistic Lock (FOR UPDATE) để tránh xung đột đồng thời
        // Customer customer = customerRepository.findByIdWithLock(customerId).orElseGet(() -> {
        //     Tạo mới customer nếu là lần issue đầu tiên
        // });
        // 3. Xử lý logic Issue / Redeem:
        // - Nếu là REDEEM: Kiểm tra xem balance hiện tại có đủ amount không. Nếu balance < amount -> Throw exception (chặn số dư âm).
        // - Tăng chain_seq lên 1 đơn vị liên tục (0, 1, 2...).
        // - Cập nhật balance mới.
                // 4. Lưu ledger_entry và outbox_event trong cùng một Transaction DB.
    }
}