package com.octapoint.core;

import com.octapoint.core.api.CreditsApiDelegate;
import com.octapoint.core.model.CreditBalance;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class MockBalanceService implements CreditsApiDelegate {

    @Override
    public ResponseEntity<CreditBalance> getCreditBalance(String customerId) {
        // Giả lập data trả về
        CreditBalance mockData = new CreditBalance();
        mockData.setCustomerId(customerId);
        mockData.setBalance(100); // Khách có 100 điểm
        mockData.setSettlementStatus("SETTLED");

        System.out.println("=> [MOCK API] Đã gọi lấy số dư cho: " + customerId);
        return ResponseEntity.ok(mockData);
    }
}