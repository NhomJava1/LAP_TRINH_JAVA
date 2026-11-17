package com.octapoint.core.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    List<OutboxEvent> findByStatusOrderByOrderKeyAscCreatedAtAsc(String status);

    List<OutboxEvent> findByStatusOrderByCreatedAtDesc(String status);

    List<OutboxEvent> findByStatus(String status);

    List<OutboxEvent> findByTenantIdAndStatus(String tenantId, String status);

    List<OutboxEvent> findTop100ByOrderByCreatedAtDesc();

    List<OutboxEvent> findByAggregateIdIn(Collection<String> aggregateIds);

    Optional<OutboxEvent> findByAggregateId(String aggregateId);

    boolean existsByTenantIdAndCustomerIdAndStatus(String tenantId, String customerId, String status);

    long countByStatus(String status);

    long countByTenantIdAndStatus(String tenantId, String status);
}
