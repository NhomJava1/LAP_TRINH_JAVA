package com.octapoint.core.admin;

import com.octapoint.core.audit.AuditLog;
import com.octapoint.core.audit.AuditService;
import com.octapoint.core.ledger.LedgerService;
import com.octapoint.core.outbox.OutboxEvent;
import com.octapoint.core.outbox.OutboxSettlementWorker;
import com.octapoint.core.security.PortalPrincipal;
import com.octapoint.core.shared.ApiException;
import com.octapoint.core.tenant.Tenant;
import com.octapoint.core.tenant.TenantService;
import com.octapoint.core.tenant.TenantStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class AdminController {

    private final TenantService tenantService;
    private final LedgerService ledgerService;
    private final OutboxSettlementWorker outboxSettlementWorker;
    private final AuditService auditService;
    private final String settlementMode;

    public AdminController(
            TenantService tenantService,
            LedgerService ledgerService,
            OutboxSettlementWorker outboxSettlementWorker,
            AuditService auditService,
            @Value("${app.settlement.mode:LIVE}") String settlementMode) {
        this.tenantService = tenantService;
        this.ledgerService = ledgerService;
        this.outboxSettlementWorker = outboxSettlementWorker;
        this.auditService = auditService;
        this.settlementMode = settlementMode != null ? settlementMode.toUpperCase() : "MOCK";
    }

    @GetMapping("/api/admin/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalTenants", tenantService.countAll());
        stats.put("pendingTenants", tenantService.countByStatus(TenantStatus.PENDING));
        stats.put("approvedTenants", tenantService.countByStatus(TenantStatus.APPROVED));
        stats.put("rejectedTenants", tenantService.countByStatus(TenantStatus.REJECTED));
        stats.put("suspendedTenants", tenantService.countByStatus(TenantStatus.SUSPENDED));
        stats.put("totalTransactions", ledgerService.countTotalTransactions());
        stats.put("pendingSettlements", outboxSettlementWorker.countByStatus("PENDING"));
        stats.put("settledSettlements", outboxSettlementWorker.countByStatus("SETTLED"));
        stats.put("failedSettlements", outboxSettlementWorker.countByStatus("FAILED"));
        stats.put("settlementMode", settlementMode);
        return ResponseEntity.ok(stats);
    }

    @GetMapping({"/api/admin/tenants", "/api/v1/admin/tenants"})
    public ResponseEntity<List<Map<String, Object>>> listTenants() {
        List<Map<String, Object>> items = tenantService.listTenants().stream()
                .map(this::toTenantDto)
                .toList();
        return ResponseEntity.ok(items);
    }

    @RequestMapping(path = "/api/admin/tenants/{id}/status", method = {RequestMethod.PATCH, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> updateTenantStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        PortalPrincipal admin = requireAdminPrincipal(request);
        String statusRaw = body.get("status");
        if (statusRaw == null || statusRaw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_STATUS", "status is required");
        }
        TenantStatus targetStatus;
        try {
            targetStatus = TenantStatus.valueOf(statusRaw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_STATUS",
                    "Allowed values: APPROVED, REJECTED, SUSPENDED");
        }

        Tenant updated = tenantService.updateStatus(id, targetStatus);
        auditService.log(updated.getId(), admin.email(), "ADMIN", "TENANT_STATUS_UPDATED",
                "TENANT", updated.getId(), "SUCCESS", "Updated status to " + updated.getStatus(), null);

        return ResponseEntity.ok(toTenantDto(updated));
    }

    @GetMapping("/api/admin/settlements")
    public ResponseEntity<List<Map<String, Object>>> listSettlements(
            @RequestParam(required = false) String status) {
        List<Map<String, Object>> items = outboxSettlementWorker.listSettlements(status).stream()
                .map(this::toSettlementDto)
                .toList();
        return ResponseEntity.ok(items);
    }

    @PostMapping("/api/admin/settlements/{eventId}/retry")
    public ResponseEntity<Map<String, Object>> retrySettlement(
            @PathVariable String eventId,
            HttpServletRequest request) {
        PortalPrincipal admin = requireAdminPrincipal(request);
        OutboxEvent retried = outboxSettlementWorker.retryFailedEvent(eventId, admin.email());
        return ResponseEntity.ok(toSettlementDto(retried));
    }

    @GetMapping("/api/admin/audit-logs")
    public ResponseEntity<List<AuditLog>> listAuditLogs() {
        return ResponseEntity.ok(auditService.listAllRecent());
    }

    private PortalPrincipal requireAdminPrincipal(HttpServletRequest request) {
        Object attr = request.getAttribute(PortalPrincipal.REQUEST_ATTR);
        if (!(attr instanceof PortalPrincipal principal) || !"ADMIN".equals(principal.role())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED.value(), "AUTH_REQUIRED", "Admin authentication required");
        }
        return principal;
    }

    private Map<String, Object> toTenantDto(Tenant t) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", t.getId());
        map.put("name", t.getName());
        map.put("email", t.getEmail());
        map.put("phone", t.getPhone());
        map.put("status", t.getStatus());
        map.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
        map.put("updatedAt", t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : null);
        return map;
    }

    private Map<String, Object> toSettlementDto(OutboxEvent evt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", evt.getId());
        map.put("tenantId", evt.getTenantId());
        map.put("customerId", evt.getCustomerId());
        map.put("transactionId", evt.getAggregateId());
        map.put("eventType", evt.getEventType());
        map.put("status", evt.getStatus());
        map.put("settlementMode", evt.getSettlementMode());
        map.put("chainSeq", evt.getChainSeq());
        map.put("txDigest", evt.getTxDigest());
        map.put("retryCount", evt.getRetryCount());
        map.put("lastError", evt.getLastError());
        map.put("createdAt", evt.getCreatedAt() != null ? evt.getCreatedAt().toString() : null);
        map.put("settledAt", evt.getSettledAt() != null ? evt.getSettledAt().toString() : null);
        return map;
    }
}
