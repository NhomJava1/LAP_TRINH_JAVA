package com.octapoint.core.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, String> {

    List<WebhookDelivery> findTop50ByTenantIdOrderByCreatedAtDesc(String tenantId);
}
