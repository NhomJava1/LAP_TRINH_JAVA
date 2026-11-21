package com.octapoint.core.apikey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, String> {

    List<ApiKeyEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    Optional<ApiKeyEntity> findByIdAndTenantId(String id, String tenantId);

    long countByTenantIdAndStatus(String tenantId, String status);
}
