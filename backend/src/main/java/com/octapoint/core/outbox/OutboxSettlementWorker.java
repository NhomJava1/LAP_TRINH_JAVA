package com.octapoint.core.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.octapoint.core.audit.AuditService;
import com.octapoint.core.chain.BalanceChangeRequest;
import com.octapoint.core.chain.ChainClient;
import com.octapoint.core.chain.ChainTransaction;
import com.octapoint.core.metrics.OctaPointMetrics;
import com.octapoint.core.realtime.SseHub;
import com.octapoint.core.shared.ApiException;
import com.octapoint.core.tenant.Tenant;
import com.octapoint.core.tenant.TenantService;
import com.octapoint.core.webhook.WebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OutboxSettlementWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxSettlementWorker.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ChainClient chainClient;
    private final TenantService tenantService;
    private final WebhookService webhookService;
    private final SseHub sseHub;
    private final AuditService auditService;
    private final OctaPointMetrics metrics;
    private final ObjectMapper objectMapper;
    private final boolean workerEnabled;
    private final int maxRetries;
    private final String settlementMode;

    public OutboxSettlementWorker(
            OutboxEventRepository outboxEventRepository,
            ChainClient chainClient,
            TenantService tenantService,
            WebhookService webhookService,
            SseHub sseHub,
            AuditService auditService,
            OctaPointMetrics metrics,
            ObjectMapper objectMapper,
            @Value("${app.settlement.worker-enabled:true}") boolean workerEnabled,
            @Value("${app.settlement.max-retries:3}") int maxRetries,
            @Value("${app.settlement.mode:LIVE}") String settlementMode) {
        this.outboxEventRepository = outboxEventRepository;
        this.chainClient = chainClient;
        this.tenantService = tenantService;
        this.webhookService = webhookService;
        this.sseHub = sseHub;
        this.auditService = auditService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.workerEnabled = workerEnabled;
        this.maxRetries = maxRetries;
        this.settlementMode = settlementMode != null ? settlementMode.toUpperCase() : "MOCK";
    }

    @Scheduled(fixedDelayString = "${app.settlement.poll-interval-ms:5000}")
    public void scheduledPoll() {
        if (!workerEnabled) {
            return;
        }
        processPendingBatch();
    }

    /**
     * Processes pending outbox events sequentially per customer while batching eligible ISSUE
     * operations into a single PTB (FR-26) and enforcing tenant gas quotas (FR-27).
     */
    public synchronized int processPendingBatch() {
        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByOrderKeyAscCreatedAtAsc("PENDING");
        if (pending.isEmpty()) {
            return 0;
        }

        Set<String> blockedCustomers = new HashSet<>();
        Set<String> seenCustomerHead = new HashSet<>();
        List<OutboxEvent> eligibleIssues = new ArrayList<>();
        List<OutboxEvent> remainingEvents = new ArrayList<>();

        for (OutboxEvent evt : pending) {
            String customerKey = customerBlockKey(evt);
            if (blockedCustomers.contains(customerKey)) {
                continue;
            }
            if (evt.getTenantId() != null && evt.getCustomerId() != null
                    && outboxEventRepository.existsByTenantIdAndCustomerIdAndStatus(evt.getTenantId(), evt.getCustomerId(), "FAILED")) {
                blockedCustomers.add(customerKey);
                continue;
            }
            if (isGasQuotaExceeded(evt)) {
                pauseForGasQuota(evt);
                blockedCustomers.add(customerKey);
                continue;
            }
            if ("ISSUE".equalsIgnoreCase(evt.getEventType()) && !seenCustomerHead.contains(customerKey)) {
                eligibleIssues.add(evt);
                seenCustomerHead.add(customerKey);
            } else {
                seenCustomerHead.add(customerKey);
                remainingEvents.add(evt);
            }
        }

        int settledCount = 0;

        // FR-26: If >= 2 head ISSUE events from distinct customers are pending, batch them in 1 Sui PTB!
        if (eligibleIssues.size() >= 2) {
            settledCount += settleIssueBatch(eligibleIssues, blockedCustomers);
        } else {
            remainingEvents.addAll(0, eligibleIssues);
        }

        for (OutboxEvent evt : remainingEvents) {
            String customerKey = customerBlockKey(evt);
            if (blockedCustomers.contains(customerKey)) {
                continue;
            }
            if (isGasQuotaExceeded(evt)) {
                pauseForGasQuota(evt);
                blockedCustomers.add(customerKey);
                continue;
            }
            int ok = settleSingle(evt);
            if (ok == 0) {
                blockedCustomers.add(customerKey);
            } else {
                settledCount += ok;
            }
        }

        return settledCount;
    }

    private int settleIssueBatch(List<OutboxEvent> batch, Set<String> blockedCustomers) {
        String effectiveMode = "LIVE".equalsIgnoreCase(settlementMode) ? "LIVE" : "MOCK";
        Instant now = Instant.now();
        List<BalanceChangeRequest> requests = new ArrayList<>();
        for (OutboxEvent evt : batch) {
            evt.setProcessingStartedAt(now);
            outboxEventRepository.save(evt);
            requests.add(new BalanceChangeRequest(customerBlockKey(evt), BigInteger.valueOf(extractPoints(evt.getPayload()))));
        }

        try {
            ChainTransaction tx = chainClient.mintBatchRequests(requests);
            long perEventGas = tx.gasMist() / Math.max(1, batch.size());
            Map<String, Long> gasPerTenant = new HashMap<>();

            int count = 0;
            for (OutboxEvent evt : batch) {
                evt.setSettlementMode(effectiveMode);
                evt.setStatus("SETTLED");
                evt.setLastError(null);
                evt.setSettledAt(Instant.now());
                evt.setGasUsedMist(perEventGas);
                evt.setTxDigest("LIVE".equals(effectiveMode) ? tx.digest() : null);
                outboxEventRepository.save(evt);

                if (evt.getTenantId() != null) {
                    gasPerTenant.merge(evt.getTenantId(), perEventGas, Long::sum);
                }
                metrics.recordSettlementSettled();
                publishSse(evt);
                webhookService.notifySettled(evt);
                count++;
            }
            for (Map.Entry<String, Long> entry : gasPerTenant.entrySet()) {
                tenantService.addGasUsed(entry.getKey(), entry.getValue());
            }
            return count;
        } catch (Exception ex) {
            log.warn("Batch PTB issue failed, falling back to single settlement: {}", ex.getMessage());
            int count = 0;
            for (OutboxEvent evt : batch) {
                int ok = settleSingle(evt);
                if (ok == 0) {
                    blockedCustomers.add(customerBlockKey(evt));
                } else {
                    count += ok;
                }
            }
            return count;
        }
    }

    private int settleSingle(OutboxEvent evt) {
        String effectiveMode = "LIVE".equalsIgnoreCase(settlementMode) ? "LIVE" : "MOCK";
        evt.setProcessingStartedAt(Instant.now());
        outboxEventRepository.save(evt);

        try {
            long points = extractPoints(evt.getPayload());
            String chainAccountId = customerBlockKey(evt);
            BalanceChangeRequest changeReq = new BalanceChangeRequest(chainAccountId, BigInteger.valueOf(points));

            ChainTransaction tx = "REDEEM".equalsIgnoreCase(evt.getEventType())
                    ? chainClient.redeem(changeReq)
                    : chainClient.mint(changeReq);

            evt.setSettlementMode(effectiveMode);
            evt.setStatus("SETTLED");
            evt.setLastError(null);
            evt.setSettledAt(Instant.now());
            evt.setGasUsedMist(tx.gasMist());
            // SRS Section 5: MOCK settlement must never attach a fake or stale Sui Testnet digest
            evt.setTxDigest("LIVE".equals(effectiveMode) ? tx.digest() : null);
            outboxEventRepository.save(evt);

            if (evt.getTenantId() != null && tx.gasMist() > 0) {
                tenantService.addGasUsed(evt.getTenantId(), tx.gasMist());
            }
            metrics.recordSettlementSettled();
            publishSse(evt);
            webhookService.notifySettled(evt);
            return 1;
        } catch (Exception ex) {
            int nextRetry = (evt.getRetryCount() != null ? evt.getRetryCount() : 0) + 1;
            evt.setRetryCount(nextRetry);
            evt.setLastError(truncateError(ex.getMessage()));
            if (nextRetry >= maxRetries) {
                evt.setStatus("FAILED");
                metrics.recordSettlementFailed();
            }
            outboxEventRepository.save(evt);
            publishSse(evt);
            log.warn("Outbox event {} failed (attempt {}/{}): {}", evt.getId(), nextRetry, maxRetries, ex.getMessage());
            return 0;
        }
    }

    private boolean isGasQuotaExceeded(OutboxEvent evt) {
        if (evt.getTenantId() == null) {
            return false;
        }
        Tenant tenant = tenantService.getTenant(evt.getTenantId());
        long quota = tenant.getGasQuotaMist() != null ? tenant.getGasQuotaMist() : Long.MAX_VALUE;
        long used = tenant.getGasUsedMist() != null ? tenant.getGasUsedMist() : 0L;
        return used >= quota;
    }

    private void pauseForGasQuota(OutboxEvent evt) {
        evt.setStatus("PAUSED_GAS_QUOTA");
        evt.setLastError("Tenant gas quota exceeded");
        outboxEventRepository.save(evt);
        publishSse(evt);
    }

    public int resumePausedGasEvents(String tenantId) {
        List<OutboxEvent> paused = outboxEventRepository.findByTenantIdAndStatus(tenantId, "PAUSED_GAS_QUOTA");
        for (OutboxEvent evt : paused) {
            evt.setStatus("PENDING");
            evt.setLastError(null);
            outboxEventRepository.save(evt);
        }
        return paused.size();
    }

    public OutboxEvent retryFailedEvent(String eventId, String adminActor) {
        OutboxEvent evt = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND.value(), "OUTBOX_EVENT_NOT_FOUND", "Outbox event not found"));
        if (!"FAILED".equals(evt.getStatus()) && !"PAUSED_GAS_QUOTA".equals(evt.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "EVENT_NOT_FAILED",
                    "Only FAILED or PAUSED_GAS_QUOTA events can be manually retried");
        }
        evt.setStatus("PENDING");
        evt.setRetryCount(0);
        evt.setLastError(null);
        evt.setProcessingStartedAt(null);
        OutboxEvent saved = outboxEventRepository.save(evt);

        auditService.log(evt.getTenantId(), adminActor, "ADMIN", "SETTLEMENT_MANUAL_RETRY",
                "OUTBOX_EVENT", evt.getId(), "SUCCESS", "Reset settlement to PENDING", evt.getAggregateId());
        return saved;
    }

    public List<OutboxEvent> listSettlements(String status) {
        if (status != null && !status.isBlank()) {
            return outboxEventRepository.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase());
        }
        return outboxEventRepository.findTop100ByOrderByCreatedAtDesc();
    }

    public long countByStatus(String status) {
        return outboxEventRepository.countByStatus(status);
    }

    private void publishSse(OutboxEvent evt) {
        if (evt.getTenantId() == null) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", evt.getId());
        payload.put("transactionId", evt.getAggregateId());
        payload.put("customerId", evt.getCustomerId());
        payload.put("status", evt.getStatus());
        payload.put("settlementMode", evt.getSettlementMode());
        payload.put("txDigest", evt.getTxDigest());
        sseHub.publish(evt.getTenantId(), "settlement-update", payload);
    }

    private long extractPoints(String payloadJson) {
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            long points = root.path("points").asLong(0L);
            if (points <= 0L) {
                throw new IllegalStateException("Outbox payload missing positive points");
            }
            return points;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse outbox payload", e);
        }
    }

    private String customerBlockKey(OutboxEvent evt) {
        return (evt.getTenantId() != null ? evt.getTenantId() : "default")
                + ":" + (evt.getCustomerId() != null ? evt.getCustomerId() : evt.getAggregateId());
    }

    private String truncateError(String msg) {
        if (msg == null) {
            return "Unknown settlement error";
        }
        return msg.length() <= 500 ? msg : msg.substring(0, 500);
    }
}
