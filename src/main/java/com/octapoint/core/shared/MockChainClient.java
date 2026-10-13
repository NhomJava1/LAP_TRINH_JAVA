package com.octapoint.core.shared;

import org.springframework.stereotype.Component;
import java.util.UUID; // Đã thêm từ khóa import chuẩn

@Component
public class MockChainClient implements ChainClient {
    @Override
    public String issueCreditOnChain(String userId, long amount) {
        // Trả về mã giao dịch giả lập của Sui Blockchain để test hệ thống trước
        return "0xMOCK_SUI_TX_" + UUID.randomUUID().toString().replace("-", "");
    }
}