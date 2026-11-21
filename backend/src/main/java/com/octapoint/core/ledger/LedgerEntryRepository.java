package com.octapoint.core.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, String> {

    List<LedgerEntry> findTop100ByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<LedgerEntry> findTop100ByTenantIdAndCustomerIdOrderByCreatedAtDesc(String tenantId, String customerId);

    List<LedgerEntry> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<LedgerEntry> findAllByOrderByCreatedAtAsc();

    long countByTenantIdAndCustomerIdAndCreatedAtAfter(String tenantId, String customerId, Instant since);

    long countByTenantIdAndCreatedAtAfter(String tenantId, Instant since);
}
