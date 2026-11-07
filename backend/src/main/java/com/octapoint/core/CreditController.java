package com.octapoint.core;

import com.octapoint.core.model.CreditBalance;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/credits")
public class CreditController {

    // Gọi trực tiếp đến class Mock của bạn (sử dụng Interface hoặc class cụ thể)
    // Vì MockBalanceService implements CreditsApiDelegate, chúng ta có thể gọi trực tiếp nó.
    
    @GetMapping("/balance")
    public ResponseEntity<CreditBalance> getBalance(@RequestParam String customerId) {
        // Tạo dữ liệu mock
        CreditBalance mockData = new CreditBalance();
        mockData.setCustomerId(customerId);
        mockData.setBalance(100);
        mockData.setSettlementStatus("SETTLED");

        System.out.println("=> [MOCK API] Đã gọi lấy số dư cho: " + customerId);
        return ResponseEntity.ok(mockData);
    }
}