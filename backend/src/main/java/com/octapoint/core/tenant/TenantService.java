package com.octapoint.core.tenant;

import com.octapoint.core.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TenantService {

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
        if (earnVndPerPoint <= 0 || redeemValueVndPerPoint <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_RULE_RATE", "Earn and redeem rates must be positive integers");
        }
        TenantRule rule = getOrCreateRule(tenantId);
        rule.setEarnVndPerPoint(earnVndPerPoint);
        rule.setRedeemValueVndPerPoint(redeemValueVndPerPoint);
        return tenantRuleRepository.save(rule);
    }
}
