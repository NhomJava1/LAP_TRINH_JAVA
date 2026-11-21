package com.octapoint.core.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRuleRepository extends JpaRepository<TenantRule, String> {

    Optional<TenantRule> findByTenantId(String tenantId);
}
