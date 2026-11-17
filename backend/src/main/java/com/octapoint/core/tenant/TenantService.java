package com.octapoint.core.tenant;

import com.octapoint.core.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TenantService {

    private static final Set<String> ALLOWED_PLANS = Set.of("STARTER", "GROWTH", "ENTERPRISE");

    private final TenantRepository tenantRepository;
    private final TenantRuleRepository tenantRuleRepository;

    public TenantService(TenantRepository tenantRepository, TenantRuleRepository tenantRuleRepository) {
        this.tenantRepository = tenantRepository;
        this.tenantRuleRepository = tenantRuleRepository;
    }

    @Transactional
    public Tenant registerTenant(String businessName, String email, String phone) {
        if (businessName == null || businessName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_TENANT_NAME", "Business name is required");
        }
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_EMAIL", "Email is required");
        }
        if (tenantRepository.findByEmail(email.trim().toLowerCase()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT.value(), "EMAIL_ALREADY_EXISTS", "Email already registered");
        }

        Tenant tenant = new Tenant();
        tenant.setId("ten_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        tenant.setName(businessName.trim());
        tenant.setEmail(email.trim().toLowerCase());
        tenant.setPhone(phone != null ? phone.trim() : null);
        tenant.setStatus(TenantStatus.PENDING.name());
        tenant.setGasQuotaMist(500_000_000L);
        tenant.setGasUsedMist(0L);
        tenant.setSubscriptionPlan("STARTER");
        Tenant saved = tenantRepository.save(tenant);

        TenantRule defaultRule = new TenantRule();
        defaultRule.setId("rule_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        defaultRule.setTenantId(saved.getId());
        defaultRule.setEarnVndPerPoint(1000L);
        defaultRule.setRedeemValueVndPerPoint(10L);
        tenantRuleRepository.save(defaultRule);

        return saved;
    }

    @Transactional
    public Tenant updateStatus(String tenantId, TenantStatus newStatus) {
        Tenant tenant = getTenant(tenantId);
        tenant.setStatus(newStatus.name());
        return tenantRepository.save(tenant);
    }

    @Transactional
    public Tenant addGasUsed(String tenantId, long gasMist) {
        Tenant tenant = getTenant(tenantId);
        if (gasMist > 0) {
            long current = tenant.getGasUsedMist() != null ? tenant.getGasUsedMist() : 0L;
            tenant.setGasUsedMist(current + gasMist);
            return tenantRepository.save(tenant);
        }
        return tenant;
    }

    @Transactional
    public Tenant updateGasQuota(String tenantId, long newQuotaMist) {
        if (newQuotaMist < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_GAS_QUOTA", "gasQuotaMist must be >= 0");
        }
        Tenant tenant = getTenant(tenantId);
        tenant.setGasQuotaMist(newQuotaMist);
        return tenantRepository.save(tenant);
    }

    @Transactional
    public Tenant updateWebhook(String tenantId, String webhookUrl, String webhookSecret) {
        Tenant tenant = getTenant(tenantId);
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            String trimmed = webhookUrl.trim();
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_WEBHOOK_URL", "Webhook URL must start with http:// or https://");
            }
            tenant.setWebhookUrl(trimmed);
            if (webhookSecret != null && !webhookSecret.isBlank()) {
                tenant.setWebhookSecret(webhookSecret.trim());
            } else if (tenant.getWebhookSecret() == null || tenant.getWebhookSecret().isBlank()) {
                tenant.setWebhookSecret("whsec_" + UUID.randomUUID().toString().replace("-", ""));
            }
        } else {
            tenant.setWebhookUrl(null);
            tenant.setWebhookSecret(null);
        }
        return tenantRepository.save(tenant);
    }

    @Transactional
    public Tenant updateSubscriptionPlan(String tenantId, String plan) {
        String normalized = plan != null ? plan.trim().toUpperCase() : "";
        if (!ALLOWED_PLANS.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_PLAN", "Allowed plans: STARTER, GROWTH, ENTERPRISE");
        }
        Tenant tenant = getTenant(tenantId);
        tenant.setSubscriptionPlan(normalized);
        return tenantRepository.save(tenant);
    }

    public Tenant getTenant(String tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND.value(), "TENANT_NOT_FOUND", "Tenant not found: " + tenantId));
    }

    public List<Tenant> listTenants() {
        return tenantRepository.findAllByOrderByCreatedAtDesc();
    }

    public long countAll() {
        return tenantRepository.count();
    }

    public long countByStatus(TenantStatus status) {
        return tenantRepository.countByStatus(status.name());
    }

    @Transactional
    public TenantRule getOrCreateRule(String tenantId) {
        return tenantRuleRepository.findByTenantId(tenantId).orElseGet(() -> {
            getTenant(tenantId);
            TenantRule rule = new TenantRule();
            rule.setId("rule_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            rule.setTenantId(tenantId);
            rule.setEarnVndPerPoint(1000L);
            rule.setRedeemValueVndPerPoint(10L);
            return tenantRuleRepository.save(rule);
        });
    }

    @Transactional
    public TenantRule updateRule(String tenantId, long earnVndPerPoint, long redeemValueVndPerPoint) {
        TenantRule existing = getOrCreateRule(tenantId);
        return updateAdvancedRule(
                tenantId,
                earnVndPerPoint,
                redeemValueVndPerPoint,
                existing.getPointExpiryDays(),
                existing.getSilverThresholdPoints(),
                existing.getGoldThresholdPoints(),
                existing.getSilverMultiplier(),
                existing.getGoldMultiplier()
        );
    }

    @Transactional
    public TenantRule updateAdvancedRule(
            String tenantId,
            long earnVndPerPoint,
            long redeemValueVndPerPoint,
            int pointExpiryDays,
            long silverThresholdPoints,
            long goldThresholdPoints,
            BigDecimal silverMultiplier,
            BigDecimal goldMultiplier) {
        if (earnVndPerPoint <= 0 || redeemValueVndPerPoint <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_RULE_RATE", "Earn and redeem rates must be positive integers");
        }
        if (pointExpiryDays <= 0 || silverThresholdPoints < 0 || goldThresholdPoints < silverThresholdPoints) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_TIER_CONFIG", "Invalid expiry days or tier thresholds");
        }
        if (silverMultiplier == null || goldMultiplier == null
                || silverMultiplier.compareTo(BigDecimal.ONE) < 0
                || goldMultiplier.compareTo(silverMultiplier) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_MULTIPLIER", "Tier multipliers must be >= 1.0 and gold >= silver");
        }

        TenantRule rule = getOrCreateRule(tenantId);
        rule.setEarnVndPerPoint(earnVndPerPoint);
        rule.setRedeemValueVndPerPoint(redeemValueVndPerPoint);
        rule.setPointExpiryDays(pointExpiryDays);
        rule.setSilverThresholdPoints(silverThresholdPoints);
        rule.setGoldThresholdPoints(goldThresholdPoints);
        rule.setSilverMultiplier(silverMultiplier);
        rule.setGoldMultiplier(goldMultiplier);
        return tenantRuleRepository.save(rule);
    }
}
