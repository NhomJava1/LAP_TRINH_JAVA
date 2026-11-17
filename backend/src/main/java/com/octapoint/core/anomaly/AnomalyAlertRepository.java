package com.octapoint.core.anomaly;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnomalyAlertRepository extends JpaRepository<AnomalyAlert, String> {

    List<AnomalyAlert> findTop50ByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<AnomalyAlert> findTop50ByOrderByCreatedAtDesc();

    long countByTenantId(String tenantId);
}
