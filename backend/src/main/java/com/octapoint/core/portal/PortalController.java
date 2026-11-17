package com.octapoint.core.portal;

import com.octapoint.core.apikey.ApiKeyEntity;
import com.octapoint.core.apikey.ApiKeyService;
import com.octapoint.core.audit.AuditService;
import com.octapoint.core.ledger.LedgerService;
import com.octapoint.core.security.PortalPrincipal;
import com.octapoint.core.shared.ApiException;
import com.octapoint.core.tenant.Tenant;
import com.octapoint.core.tenant.TenantRule;
import com.octapoint.core.tenant.TenantService;
import com.octapoint.core.tenant.TenantStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class PortalController {

    private final TenantService tenantService;
    private final ApiKeyService apiKeyService;
    private final LedgerService ledgerService;
    private final AuditService auditService;

    public PortalController(
            TenantService tenantService,
            ApiKeyService apiKeyService,
            LedgerService ledgerService,
            AuditService auditService) {
        this.tenantService = tenantService;
        this.apiKeyService = apiKeyService;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
    }

    @GetMapping("/api/portal/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard(HttpServletRequest request) {
        PortalPrincipal principal = requireMerchantPrincipal(request);
        Tenant tenant = tenantService.getTenant(principal.tenantId());
        TenantRule rule = tenantService.getOrCreateRule(tenant.getId());
        Map<String, Object> stats = ledgerService.getTenantDashboardStats(tenant.getId());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tenant", toTenantDto(tenant));
        response.put("rule", toRuleDto(rule));
        response.put("stats", stats);
        response.put("activeApiKeys", apiKeyService.countActiveByTenant(tenant.getId()));
        return ResponseEntity.ok(response);
    }

    @GetMapping({"/api/portal/api-keys", "/api/v1/merchants/api-keys"})
    public ResponseEntity<List<Map<String, Object>>> listApiKeys(HttpServletRequest request) {
        PortalPrincipal principal = requireMerchantPrincipal(request);
        List<Map<String, Object>> items = apiKeyService.listByTenant(principal.tenantId()).stream()
                .map(this::toSafeApiKeyDto)
                .toList();
        return ResponseEntity.ok(items);
    }

    @PostMapping({"/api/portal/api-keys", "/api/v1/merchants/api-keys"})
    public ResponseEntity<Map<String, Object>> createApiKey(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {
        PortalPrincipal principal = requireMerchantPrincipal(request);
        Tenant tenant = requireApprovedTenant(principal.tenantId());
        String keyName = body != null ? body.get("name") : null;

        ApiKeyService.CreatedApiKey created = apiKeyService.createApiKey(tenant.getId(), keyName);
        auditService.log(tenant.getId(), principal.email(), "MERCHANT", "API_KEY_CREATED",
                "API_KEY", created.id(), "SUCCESS", "Created API key " + created.name(), null);

        return ResponseEntity.status(HttpStatus.CREATED).body(toCreatedApiKeyDto(created));
    }

    @PostMapping("/api/portal/api-keys/{keyId}/rotate")
    public ResponseEntity<Map<String, Object>> rotateApiKey(
            @PathVariable String keyId,
            HttpServletRequest request) {
        PortalPrincipal principal = requireMerchantPrincipal(request);
        Tenant tenant = requireApprovedTenant(principal.tenantId());

        ApiKeyService.CreatedApiKey rotated = apiKeyService.rotateApiKey(tenant.getId(), keyId);
        auditService.log(tenant.getId(), principal.email(), "MERCHANT", "API_KEY_ROTATED",
                "API_KEY", rotated.id(), "SUCCESS", "Rotated key " + keyId + " -> " + rotated.id(), null);

        return ResponseEntity.ok(toCreatedApiKeyDto(rotated));
    }

    @PostMapping("/api/portal/api-keys/{keyId}/revoke")
    public ResponseEntity<Map<String, Object>> revokeApiKey(
            @PathVariable String keyId,
            HttpServletRequest request) {
        PortalPrincipal principal = requireMerchantPrincipal(request);
        ApiKeyEntity revoked = apiKeyService.revokeApiKey(principal.tenantId(), keyId);
        auditService.log(principal.tenantId(), principal.email(), "MERCHANT", "API_KEY_REVOKED",
                "API_KEY", revoked.getId(), "SUCCESS", "Revoked API key", null);

        return ResponseEntity.ok(toSafeApiKeyDto(revoked));
    }

    @GetMapping("/api/portal/rules")
    public ResponseEntity<Map<String, Object>> getRules(HttpServletRequest request) {
        PortalPrincipal principal = requireMerchantPrincipal(request);
        TenantRule rule = tenantService.getOrCreateRule(principal.tenantId());
        return ResponseEntity.ok(toRuleDto(rule));
    }

    @PutMapping("/api/portal/rules")
    public ResponseEntity<Map<String, Object>> updateRules(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        PortalPrincipal principal = requireMerchantPrincipal(request);
        long earnRate = parsePositiveLong(body.get("earnVndPerPoint"), "earnVndPerPoint");
        long redeemRate = parsePositiveLong(body.get("redeemValueVndPerPoint"), "redeemValueVndPerPoint");

        TenantRule updated = tenantService.updateRule(principal.tenantId(), earnRate, redeemRate);
        auditService.log(principal.tenantId(), principal.email(), "MERCHANT", "RULE_UPDATED",
                "TENANT_RULE", updated.getId(), "SUCCESS",
                "earnVndPerPoint=" + earnRate + ", redeemValueVndPerPoint=" + redeemRate, null);

        return ResponseEntity.ok(toRuleDto(updated));
    }

    @GetMapping("/api/portal/transactions")
    public ResponseEntity<List<Map<String, Object>>> listTransactions(
            @RequestParam(required = false) String customerId,
            HttpServletRequest request) {
        PortalPrincipal principal = requireMerchantPrincipal(request);
        return ResponseEntity.ok(ledgerService.getHistory(principal.tenantId(), customerId));
    }

    @GetMapping("/api/portal/customers")
    public ResponseEntity<List<Map<String, Object>>> listCustomers(HttpServletRequest request) {
        PortalPrincipal principal = requireMerchantPrincipal(request);
        return ResponseEntity.ok(ledgerService.listCustomers(principal.tenantId()));
    }

    private PortalPrincipal requireMerchantPrincipal(HttpServletRequest request) {
        Object attr = request.getAttribute(PortalPrincipal.REQUEST_ATTR);
        if (!(attr instanceof PortalPrincipal principal) || principal.tenantId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED.value(), "AUTH_REQUIRED", "Merchant authentication required");
        }
        return principal;
    }

    private Tenant requireApprovedTenant(String tenantId) {
        Tenant tenant = tenantService.getTenant(tenantId);
        if (tenant.getStatusEnum() != TenantStatus.APPROVED) {
            throw new ApiException(HttpStatus.FORBIDDEN.value(), "TENANT_NOT_APPROVED",
                    "Tenant must be APPROVED before managing API keys (status=" + tenant.getStatus() + ")");
        }
        return tenant;
    }

    private long parsePositiveLong(Object raw, String field) {
        if (!(raw instanceof Number num) || num.longValue() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_RULE_RATE", field + " must be a positive integer");
        }
        return num.longValue();
    }

    private Map<String, Object> toTenantDto(Tenant tenant) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", tenant.getId());
        map.put("name", tenant.getName());
        map.put("email", tenant.getEmail());
        map.put("phone", tenant.getPhone());
        map.put("status", tenant.getStatus());
        map.put("createdAt", tenant.getCreatedAt() != null ? tenant.getCreatedAt().toString() : null);
        return map;
    }

    private Map<String, Object> toRuleDto(TenantRule rule) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rule.getId());
        map.put("tenantId", rule.getTenantId());
        map.put("earnVndPerPoint", rule.getEarnVndPerPoint());
        map.put("redeemValueVndPerPoint", rule.getRedeemValueVndPerPoint());
        map.put("updatedAt", rule.getUpdatedAt() != null ? rule.getUpdatedAt().toString() : null);
        return map;
    }

    private Map<String, Object> toSafeApiKeyDto(ApiKeyEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("apiKey", entity.getId());
        map.put("name", entity.getName() != null ? entity.getName() : "Merchant Key");
        map.put("status", entity.getStatus());
        map.put("scopes", entity.getScopes());
        map.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        map.put("revokedAt", entity.getRevokedAt() != null ? entity.getRevokedAt().toString() : null);
        return map;
    }

    private Map<String, Object> toCreatedApiKeyDto(ApiKeyService.CreatedApiKey created) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", created.id());
        map.put("apiKey", created.apiKey());
        map.put("apiSecret", created.apiSecret());
        map.put("name", created.name());
        map.put("status", created.status());
        map.put("scopes", created.scopes());
        map.put("createdAt", created.createdAt() != null ? created.createdAt().toString() : null);
        return map;
    }
}
