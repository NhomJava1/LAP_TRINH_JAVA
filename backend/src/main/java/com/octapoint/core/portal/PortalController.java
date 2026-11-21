package com.octapoint.core.portal;

import com.octapoint.core.anomaly.AnomalyService;
import com.octapoint.core.apikey.ApiKeyEntity;
import com.octapoint.core.apikey.ApiKeyService;
import com.octapoint.core.audit.AuditService;
import com.octapoint.core.ledger.LedgerService;
import com.octapoint.core.realtime.SseHub;
import com.octapoint.core.security.AppUser;
import com.octapoint.core.security.AppUserRepository;
import com.octapoint.core.security.PortalPrincipal;
import com.octapoint.core.shared.ApiException;
import com.octapoint.core.tenant.Tenant;
import com.octapoint.core.tenant.TenantRule;
import com.octapoint.core.tenant.TenantService;
import com.octapoint.core.tenant.TenantStatus;
import com.octapoint.core.webhook.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class PortalController {

    private final TenantService tenantService;
    private final ApiKeyService apiKeyService;
    private final LedgerService ledgerService;
    private final AuditService auditService;
    private final AnomalyService anomalyService;
    private final WebhookService webhookService;
    private final SseHub sseHub;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public PortalController(
            TenantService tenantService,
            ApiKeyService apiKeyService,
            LedgerService ledgerService,
            AuditService auditService,
            AnomalyService anomalyService,
            WebhookService webhookService,
            SseHub sseHub,
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder) {
        this.tenantService = tenantService;
        this.apiKeyService = apiKeyService;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
        this.anomalyService = anomalyService;
        this.webhookService = webhookService;
        this.sseHub = sseHub;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/api/portal/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard(HttpServletRequest request) {
        PortalPrincipal principal = requirePortalPrincipal(request);
        Tenant tenant = tenantService.getTenant(principal.tenantId());
        TenantRule rule = tenantService.getOrCreateRule(tenant.getId());
        Map<String, Object> stats = ledgerService.getTenantDashboardStats(tenant.getId());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tenant", toTenantDto(tenant));
        response.put("rule", toRuleDto(rule));
        response.put("stats", stats);
        response.put("activeApiKeys", apiKeyService.countActiveByTenant(tenant.getId()));
        response.put("role", principal.role());
        return ResponseEntity.ok(response);
    }

    @GetMapping({ "/api/portal/api-keys", "/api/v1/merchants/api-keys" })
    public ResponseEntity<List<Map<String, Object>>> listApiKeys(HttpServletRequest request) {
        PortalPrincipal principal = requireMerchantOwner(request);
        List<Map<String, Object>> items = apiKeyService.listByTenant(principal.tenantId()).stream()
                .map(this::toSafeApiKeyDto)
                .toList();
        return ResponseEntity.ok(items);
    }

    @PostMapping({ "/api/portal/api-keys", "/api/v1/merchants/api-keys" })
    public ResponseEntity<Map<String, Object>> createApiKey(
            @RequestBody(required = false) CreateApiKeyRequest body,
            HttpServletRequest request) {
        PortalPrincipal principal = requireMerchantOwner(request);
        Tenant tenant = requireApprovedTenant(principal.tenantId());
        String keyName = body != null ? body.name() : null;
        String scopes = body != null ? body.scopes() : null;

        ApiKeyService.CreatedApiKey created = apiKeyService.createApiKey(tenant.getId(), keyName, scopes);
        auditService.log(tenant.getId(), principal.email(), "MERCHANT", "API_KEY_CREATED",
                "API_KEY", created.id(), "SUCCESS", "Created API key " + created.name() + " scopes=" + created.scopes(),
                null);

        return ResponseEntity.status(HttpStatus.CREATED).body(toCreatedApiKeyDto(created));
    }

    @PostMapping("/api/portal/api-keys/{keyId}/rotate")
    public ResponseEntity<Map<String, Object>> rotateApiKey(
            @PathVariable String keyId,
            HttpServletRequest request) {
        PortalPrincipal principal = requireMerchantOwner(request);
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
        PortalPrincipal principal = requireMerchantOwner(request);
        ApiKeyEntity revoked = apiKeyService.revokeApiKey(principal.tenantId(), keyId);
        auditService.log(principal.tenantId(), principal.email(), "MERCHANT", "API_KEY_REVOKED",
                "API_KEY", revoked.getId(), "SUCCESS", "Revoked API key", null);

        return ResponseEntity.ok(toSafeApiKeyDto(revoked));
    }

    @GetMapping("/api/portal/rules")
    public ResponseEntity<Map<String, Object>> getRules(HttpServletRequest request) {
        PortalPrincipal principal = requirePortalPrincipal(request);
        TenantRule rule = tenantService.getOrCreateRule(principal.tenantId());
        return ResponseEntity.ok(toRuleDto(rule));
    }

    @PutMapping("/api/portal/rules")
    public ResponseEntity<Map<String, Object>> updateRules(
            @RequestBody UpdateRuleRequest body,
            HttpServletRequest request) {
        PortalPrincipal principal = requireMerchantOwner(request);
        TenantRule current = tenantService.getOrCreateRule(principal.tenantId());
        long earnRate = parsePositiveLong(body != null ? body.earnVndPerPoint() : null, "earnVndPerPoint");
        long redeemRate = parsePositiveLong(body != null ? body.redeemValueVndPerPoint() : null,
                "redeemValueVndPerPoint");

        int expiryDays = (body != null && body.pointExpiryDays() != null)
                ? body.pointExpiryDays()
                : current.getPointExpiryDays();
        long silverThreshold = (body != null && body.silverThresholdPoints() != null)
                ? body.silverThresholdPoints()
                : current.getSilverThresholdPoints();
        long goldThreshold = (body != null && body.goldThresholdPoints() != null)
                ? body.goldThresholdPoints()
                : current.getGoldThresholdPoints();
        BigDecimal silverMult = (body != null && body.silverMultiplier() != null)
                ? body.silverMultiplier()
                : current.getSilverMultiplier();
        BigDecimal goldMult = (body != null && body.goldMultiplier() != null)
                ? body.goldMultiplier()
                : current.getGoldMultiplier();

        TenantRule updated = tenantService.updateAdvancedRule(
                principal.tenantId(),
                earnRate,
                redeemRate,
                expiryDays,
                silverThreshold,
                goldThreshold,
                silverMult,
                goldMult);
        auditService.log(principal.tenantId(), principal.email(), "MERCHANT", "RULE_UPDATED",
                "TENANT_RULE", updated.getId(), "SUCCESS",
                "earnVndPerPoint=" + earnRate + ", redeemValueVndPerPoint=" + redeemRate
                        + ", silver=" + silverMult + "x@" + silverThreshold
                        + ", gold=" + goldMult + "x@" + goldThreshold,
                null);

        return ResponseEntity.ok(toRuleDto(updated));
    }

    @GetMapping("/api/portal/transactions")
    public ResponseEntity<List<Map<String, Object>>> listTransactions(
            @RequestParam(required = false) String customerId,
            HttpServletRequest request) {
        PortalPrincipal principal = requirePortalPrincipal(request);
        return ResponseEntity.ok(ledgerService.getHistory(principal.tenantId(), customerId));
    }

    @GetMapping("/api/portal/customers")
    public ResponseEntity<List<Map<String, Object>>> listCustomers(HttpServletRequest request) {
        PortalPrincipal principal = requirePortalPrincipal(request);
        return ResponseEntity.ok(ledgerService.listCustomers(principal.tenantId()));
    }

    @GetMapping("/api/portal/analytics")
    public ResponseEntity<Map<String, Object>> analytics(HttpServletRequest request) {
        PortalPrincipal principal = requirePortalPrincipal(request);
        return ResponseEntity.ok(ledgerService.getTenantAnalytics(principal.tenantId()));
    }

    @GetMapping("/api/portal/anomalies")
    public ResponseEntity<?> anomalies(HttpServletRequest request) {
        PortalPrincipal principal = requirePortalPrincipal(request);
        return ResponseEntity.ok(anomalyService.listByTenant(principal.tenantId()));
    }

    @GetMapping(path = "/api/portal/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(HttpServletRequest request) {
        PortalPrincipal principal = requirePortalPrincipal(request);
        return sseHub.subscribe(principal.tenantId());
    }

    @GetMapping("/api/portal/webhook")
    public ResponseEntity<Map<String, Object>> getWebhook(HttpServletRequest request) {
        PortalPrincipal principal = requireMerchantOwner(request);
        Tenant tenant = tenantService.getTenant(principal.tenantId());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("webhookUrl", tenant.getWebhookUrl());
        map.put("hasSecret", tenant.getWebhookSecret() != null && !tenant.getWebhookSecret().isBlank());
        return ResponseEntity.ok(map);
    }

    @PutMapping("/api/portal/webhook")
    public ResponseEntity<Map<String, Object>> updateWebhook(
            @RequestBody UpdateWebhookRequest body,
            HttpServletRequest request) {
        PortalPrincipal principal = requireMerchantOwner(request);
        requireApprovedTenant(principal.tenantId());
        String webhookUrl = body != null ? body.webhookUrl() : null;
        String webhookSecret = body != null ? body.webhookSecret() : null;
        Tenant updated = tenantService.updateWebhook(
                principal.tenantId(), webhookUrl, webhookSecret);
        auditService.log(principal.tenantId(), principal.email(), "MERCHANT", "WEBHOOK_UPDATED",
                "TENANT", updated.getId(), "SUCCESS", "url=" + updated.getWebhookUrl(), null);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("webhookUrl", updated.getWebhookUrl());
        map.put("hasSecret", updated.getWebhookSecret() != null && !updated.getWebhookSecret().isBlank());
        return ResponseEntity.ok(map);
    }

    @GetMapping("/api/portal/webhook/deliveries")
    public ResponseEntity<?> webhookDeliveries(HttpServletRequest request) {
        PortalPrincipal principal = requireMerchantOwner(request);
        return ResponseEntity.ok(webhookService.listByTenant(principal.tenantId()));
    }

    @PostMapping("/api/portal/webhook/test")
    public ResponseEntity<?> testWebhook(HttpServletRequest request) {
        PortalPrincipal principal = requireMerchantOwner(request);
        requireApprovedTenant(principal.tenantId());
        return ResponseEntity.ok(webhookService.sendTestWebhook(principal.tenantId()));
    }

    @GetMapping("/api/portal/staff")
    public ResponseEntity<List<Map<String, Object>>> listStaff(HttpServletRequest request) {
        PortalPrincipal principal = requireMerchantOwner(request);
        List<Map<String, Object>> list = appUserRepository.findByTenantIdOrderByCreatedAtDesc(principal.tenantId())
                .stream()
                .map(u -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", u.getId());
                    row.put("name", u.getName());
                    row.put("email", u.getEmail());
                    row.put("role", u.getRole());
                    row.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
                    return row;
                }).toList();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/api/portal/staff")
    public ResponseEntity<Map<String, Object>> createStaff(
            @RequestBody CreateStaffRequest body,
            HttpServletRequest request) {
        PortalPrincipal principal = requireMerchantOwner(request);
        requireApprovedTenant(principal.tenantId());
        String name = body != null ? body.name() : null;
        String email = body != null ? body.email() : null;
        String password = body != null && body.password() != null ? body.password().trim() : null;
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_STAFF_INPUT",
                    "email and password are required");
        }
        if (password.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_PASSWORD",
                    "Password must be at least 8 characters");
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (appUserRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT.value(), "EMAIL_ALREADY_EXISTS", "Email already registered");
        }

        AppUser staff = new AppUser();
        staff.setId("usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        staff.setTenantId(principal.tenantId());
        staff.setName((name != null && !name.isBlank()) ? name.trim() : normalizedEmail);
        staff.setEmail(normalizedEmail);
        staff.setPasswordHash(passwordEncoder.encode(password));
        staff.setRole("STAFF");
        AppUser saved = appUserRepository.save(staff);

        auditService.log(principal.tenantId(), principal.email(), "MERCHANT", "STAFF_CREATED",
                "USER", saved.getId(), "SUCCESS", "Created STAFF user " + saved.getEmail(), null);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", saved.getId());
        row.put("name", saved.getName());
        row.put("email", saved.getEmail());
        row.put("role", saved.getRole());
        row.put("createdAt", saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : null);
        return ResponseEntity.status(HttpStatus.CREATED).body(row);
    }

    @GetMapping("/api/portal/billing")
    public ResponseEntity<Map<String, Object>> getBilling(HttpServletRequest request) {
        PortalPrincipal principal = requireMerchantOwner(request);
        Tenant tenant = tenantService.getTenant(principal.tenantId());
        Map<String, Object> stats = ledgerService.getTenantDashboardStats(tenant.getId());
        long txCount = ((Number) stats.getOrDefault("totalIssuedPoints", 0L)).longValue() > 0
                ? ledgerService.getHistory(tenant.getId(), null).size()
                : 0L;

        String plan = tenant.getSubscriptionPlan() != null ? tenant.getSubscriptionPlan() : "STARTER";
        long baseFeeVnd = switch (plan) {
            case "ENTERPRISE" -> 4_990_000L;
            case "GROWTH" -> 1_490_000L;
            default -> 0L;
        };
        long perTxFeeVnd = txCount * 500L;
        long totalEstimatedVnd = baseFeeVnd + perTxFeeVnd;

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("plan", plan);
        res.put("gasQuotaMist", tenant.getGasQuotaMist());
        res.put("gasUsedMist", tenant.getGasUsedMist());
        res.put("monthlyTransactions", txCount);
        res.put("baseFeeVnd", baseFeeVnd);
        res.put("usageFeeVnd", perTxFeeVnd);
        res.put("totalInvoiceVnd", totalEstimatedVnd);
        res.put("invoicePeriod", Instant.now().toString().substring(0, 7));
        res.put("billingMode", "SIMULATED");
        return ResponseEntity.ok(res);
    }

    @PutMapping("/api/portal/billing/plan")
    public ResponseEntity<Map<String, Object>> updatePlan(
            @RequestBody UpdatePlanRequest body,
            HttpServletRequest request) {
        PortalPrincipal principal = requireMerchantOwner(request);
        requireApprovedTenant(principal.tenantId());
        String plan = body != null ? body.plan() : null;
        Tenant updated = tenantService.updateSubscriptionPlan(principal.tenantId(), plan);
        auditService.log(principal.tenantId(), principal.email(), "MERCHANT", "PLAN_UPDATED",
                "TENANT", updated.getId(), "SUCCESS", "plan=" + updated.getSubscriptionPlan(), null);
        return ResponseEntity.ok(Map.of("plan", updated.getSubscriptionPlan()));
    }

    private PortalPrincipal requirePortalPrincipal(HttpServletRequest request) {
        Object attr = request.getAttribute(PortalPrincipal.REQUEST_ATTR);
        if (!(attr instanceof PortalPrincipal principal) || principal.tenantId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED.value(), "AUTH_REQUIRED", "Portal authentication required");
        }
        return principal;
    }

    private PortalPrincipal requireMerchantOwner(HttpServletRequest request) {
        PortalPrincipal principal = requirePortalPrincipal(request);
        if (!"MERCHANT".equals(principal.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN.value(), "FORBIDDEN_MERCHANT_OWNER_ONLY",
                    "Only MERCHANT owner can perform this action (current role=" + principal.role() + ")");
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
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_RULE_RATE",
                    field + " must be a positive integer");
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
        map.put("gasQuotaMist", tenant.getGasQuotaMist());
        map.put("gasUsedMist", tenant.getGasUsedMist());
        map.put("subscriptionPlan", tenant.getSubscriptionPlan());
        map.put("createdAt", tenant.getCreatedAt() != null ? tenant.getCreatedAt().toString() : null);
        return map;
    }

    private Map<String, Object> toRuleDto(TenantRule rule) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rule.getId());
        map.put("tenantId", rule.getTenantId());
        map.put("earnVndPerPoint", rule.getEarnVndPerPoint());
        map.put("redeemValueVndPerPoint", rule.getRedeemValueVndPerPoint());
        map.put("pointExpiryDays", rule.getPointExpiryDays());
        map.put("silverThresholdPoints", rule.getSilverThresholdPoints());
        map.put("goldThresholdPoints", rule.getGoldThresholdPoints());
        map.put("silverMultiplier", rule.getSilverMultiplier());
        map.put("goldMultiplier", rule.getGoldMultiplier());
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

    public record CreateApiKeyRequest(String name, String scopes) {
    }

    public record UpdateRuleRequest(
            Long earnVndPerPoint,
            Long redeemValueVndPerPoint,
            Integer pointExpiryDays,
            Long silverThresholdPoints,
            Long goldThresholdPoints,
            BigDecimal silverMultiplier,
            BigDecimal goldMultiplier) {
    }

    public record UpdateWebhookRequest(String webhookUrl, String webhookSecret) {
    }

    public record CreateStaffRequest(String name, String email, String password) {
    }

    public record UpdatePlanRequest(String plan) {
    }
}
