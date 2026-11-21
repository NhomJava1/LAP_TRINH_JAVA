package com.octapoint.core.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    List<AuditLog> findTop100ByOrderByCreatedAtDesc();

    List<AuditLog> findTop100ByTenantIdOrderByCreatedAtDesc(String tenantId);
}
