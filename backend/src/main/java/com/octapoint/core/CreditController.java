package com.octapoint.core;

import com.octapoint.core.ledger.LedgerService;
import com.octapoint.core.model.CreditBalance;
import com.octapoint.core.security.PortalPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/credits")
public class CreditController {

    private final LedgerService ledgerService;

    public CreditController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/balance")
    public ResponseEntity<CreditBalance> getBalance(
            @RequestParam String customerId,
            @RequestParam(required = false) String tenantId,
            HttpServletRequest request) {
        PortalPrincipal principal = (PortalPrincipal) request.getAttribute(PortalPrincipal.REQUEST_ATTR);
        String effectiveTenantId = (principal != null && principal.tenantId() != null)
                ? principal.tenantId()
                : null;
        CreditBalance dto = new CreditBalance();
        dto.setCustomerId(customerId);
        if (effectiveTenantId != null && !effectiveTenantId.isBlank()) {
            Map<String, Object> real = ledgerService.getBalance(effectiveTenantId.trim(), customerId);
            Number bal = (Number) real.get("balance");
            dto.setBalance(bal != null ? bal.intValue() : 0);
            String st = String.valueOf(real.get("settlementStatus"));
            dto.setSettlementStatus(CreditBalance.SettlementStatusEnum.fromValue(st));
        } else {
            dto.setBalance(0);
            dto.setSettlementStatus(CreditBalance.SettlementStatusEnum.SETTLED);
        }
        return ResponseEntity.ok(dto);
    }
}
