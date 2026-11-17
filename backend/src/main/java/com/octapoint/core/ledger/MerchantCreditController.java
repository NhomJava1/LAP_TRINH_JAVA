package com.octapoint.core.ledger;

import com.octapoint.core.audit.AuditService;
import com.octapoint.core.security.MerchantAuthContext;
import com.octapoint.core.shared.ApiException;
import com.octapoint.core.tenant.TenantRule;
import com.octapoint.core.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/merchant/v1/credits")
public class MerchantCreditController {

    private final LedgerService ledgerService;
    private final TenantService tenantService;
    private final AuditService auditService;

    public MerchantCreditController(
            LedgerService ledgerService,
            TenantService tenantService,
            AuditService auditService) {
        this.ledgerService = ledgerService;
        this.tenantService = tenantService;
        this.auditService = auditService;
    }

    @PostMapping("/issue")
    public ResponseEntity<Map<String, Object>> issue(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        MerchantAuthContext auth = requireScope(request, "ISSUE");
        String customerId = asString(body.get("customerId"));
        String orderId = firstNonBlank(asString(body.get("orderId")), asString(body.get("businessTransactionId")));
        Long amountVnd = asLong(body.get("amountVnd"));

        Map<String, Object> result = ledgerService.issue(
                auth.tenantId(), customerId, orderId, amountVnd, idempotencyKey, auth.keyId());
        auditService.log(auth.tenantId(), auth.keyId(), "API_KEY", "CREDIT_ISSUE", "CUSTOMER", customerId,
                "SUCCESS", "Issued " + result.get("pointsIssued") + " points (seq=" + result.get("chainSeq") + ")",
                String.valueOf(result.get("transactionId")));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/redeem")
    public ResponseEntity<Map<String, Object>> redeem(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        MerchantAuthContext auth = requireScope(request, "REDEEM");
        String customerId = asString(body.get("customerId"));
        String orderId = firstNonBlank(asString(body.get("orderId")), asString(body.get("businessTransactionId")));
        Long pointsToRedeem = asLong(body.get("pointsToRedeem"));

        try {
            Map<String, Object> result = ledgerService.redeem(
                    auth.tenantId(), customerId, orderId, pointsToRedeem, idempotencyKey, auth.keyId());
            auditService.log(auth.tenantId(), auth.keyId(), "API_KEY", "CREDIT_REDEEM", "CUSTOMER", customerId,
                    "SUCCESS", "Redeemed " + pointsToRedeem + " points (seq=" + result.get("chainSeq") + ")",
                    String.valueOf(result.get("transactionId")));
            return ResponseEntity.ok(result);
        } catch (ApiException ex) {
            if (ex.getStatus() == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
                auditService.log(auth.tenantId(), auth.keyId(), "API_KEY", "REDEEM_REJECTED_INSUFFICIENT_BALANCE",
                        "CUSTOMER", customerId, "REJECTED", ex.getMessage(), null);
            }
            throw ex;
        }
    }

    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> balance(
            @RequestParam String customerId,
            HttpServletRequest request) {
        MerchantAuthContext auth = requireScope(request, "READ");
        return ResponseEntity.ok(ledgerService.getBalance(auth.tenantId(), customerId));
    }

    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> history(
            @RequestParam(required = false) String customerId,
            HttpServletRequest request) {
        MerchantAuthContext auth = requireScope(request, "READ");
        return ResponseEntity.ok(ledgerService.getHistory(auth.tenantId(), customerId));
    }

    @GetMapping("/rules")
    public ResponseEntity<Map<String, Object>> rules(HttpServletRequest request) {
        MerchantAuthContext auth = requireScope(request, "READ");
        TenantRule rule = tenantService.getOrCreateRule(auth.tenantId());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tenantId", rule.getTenantId());
        map.put("earnVndPerPoint", rule.getEarnVndPerPoint());
        map.put("redeemValueVndPerPoint", rule.getRedeemValueVndPerPoint());
        map.put("pointExpiryDays", rule.getPointExpiryDays());
        map.put("silverThresholdPoints", rule.getSilverThresholdPoints());
        map.put("goldThresholdPoints", rule.getGoldThresholdPoints());
        map.put("silverMultiplier", rule.getSilverMultiplier());
        map.put("goldMultiplier", rule.getGoldMultiplier());
        return ResponseEntity.ok(map);
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(HttpServletRequest request) {
        MerchantAuthContext auth = requireScope(request, "READ");
        Map<String, Object> res = new LinkedHashMap<>(ledgerService.getTenantDashboardStats(auth.tenantId()));
        res.put("tenantId", auth.tenantId());
        return ResponseEntity.ok(res);
    }

    private MerchantAuthContext requireScope(HttpServletRequest request, String requiredScope) {
        Object attr = request.getAttribute(MerchantAuthContext.REQUEST_ATTR);
        if (!(attr instanceof MerchantAuthContext ctx)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED.value(), "AUTH_REQUIRED", "Merchant HMAC authentication required");
        }
        if (!ctx.hasScope(requiredScope)) {
            throw new ApiException(HttpStatus.FORBIDDEN.value(), "INSUFFICIENT_SCOPE",
                    "API key missing required scope: " + requiredScope);
        }
        return ctx;
    }

    private String asString(Object val) {
        return val != null ? String.valueOf(val) : null;
    }

    private Long asLong(Object val) {
        if (val instanceof Number num) {
            return num.longValue();
        }
        if (val instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return (fallback != null && !fallback.isBlank()) ? fallback.trim() : null;
    }
}
