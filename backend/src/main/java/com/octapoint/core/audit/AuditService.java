package com.octapoint.core.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(
            String tenantId,
            String actor,
            String actorRole,
            String action,
            String targetType,
            String targetId,
            String result,
            String details,
            String correlationId) {
        AuditLog entry = new AuditLog();
        entry.setId("aud_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        entry.setTenantId(tenantId);
        entry.setActor(actor != null ? actor : "system");
        entry.setActorRole(actorRole);
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setResult(result);
        entry.setDetails(details);
        entry.setCorrelationId(correlationId != null ? correlationId : UUID.randomUUID().toString());
        auditLogRepository.save(entry);
    }

    public List<AuditLog> listAllRecent() {
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc();
    }

    public List<AuditLog> listByTenant(String tenantId) {
        return auditLogRepository.findTop100ByTenantIdOrderByCreatedAtDesc(tenantId);
    }
}
