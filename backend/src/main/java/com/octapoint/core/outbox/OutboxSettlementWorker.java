package com.octapoint.core.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.octapoint.core.audit.AuditService;
import com.octapoint.core.chain.BalanceChangeRequest;
import com.octapoint.core.chain.ChainClient;
import com.octapoint.core.chain.ChainTransaction;
import com.octapoint.core.shared.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class OutboxSettlementWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxSettlementWorker.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ChainClient chainClient;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final boolean workerEnabled;
    private final int maxRetries;
    private final String settlementMode;

    public OutboxSettlementWorker(
            OutboxEventRepository outboxEventRepository,
            ChainClient chainClient,
            AuditService auditService,
            ObjectMapper objectMapper,
            @Value("${app.settlement.worker-enabled:true}") boolean workerEnabled,
            @Value("${app.settlement.max-retries:3}") int maxRetries,
            @Value("${app.settlement.mode:LIVE}") String settlementMode) {
        this.outboxEventRepository = outboxEventRepository;
        this.chainClient = chainClient;
        this.auditService = auditService;
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
     * Processes pending outbox events sequentially per customer while keeping DB transactions short
     * (never holding a transaction open during external chain RPC calls).
     */
    public synchronized int processPendingBatch() {
        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByOrderKeyAscCreatedAtAsc("PENDING");
        if (pending.isEmpty()) {
            return 0;
        }

        Set<String> blockedCustomers = new HashSet<>();
        int settledCount = 0;

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

            evt.setProcessingStartedAt(Instant.now());
            outboxEventRepository.save(evt);

            try {
                long points = extractPoints(evt.getPayload());
                String chainAccountId = customerKey;
                BalanceChangeRequest changeReq = new BalanceChangeRequest(chainAccountId, BigInteger.valueOf(points));

                ChainTransaction tx = "REDEEM".equalsIgnoreCase(evt.getEventType())
                        ? chainClient.redeem(changeReq)
                        : chainClient.mint(changeReq);

                String effectiveMode = "LIVE".equalsIgnoreCase(settlementMode) ? "LIVE" : "MOCK";
                evt.setSettlementMode(effectiveMode);
                evt.setStatus("SETTLED");
                evt.setLastError(null);
                evt.setSettledAt(Instant.now());
                // SRS Section 5: MOCK settlement must never attach a fake or stale Sui Testnet digest
                evt.setTxDigest("LIVE".equals(effectiveMode) ? tx.digest() : null);
                outboxEventRepository.save(evt);
                settledCount++;
            } catch (Exception ex) {
                int nextRetry = (evt.getRetryCount() != null ? evt.getRetryCount() : 0) + 1;
                evt.setRetryCount(nextRetry);
                evt.setLastError(truncateError(ex.getMessage()));
                if (nextRetry >= maxRetries) {
                    evt.setStatus("FAILED");
                }
                outboxEventRepository.save(evt);
                // Head-of-line blocking: prevent later events for the same customer from overtaking
                blockedCustomers.add(customerKey);
                log.warn("Outbox event {} failed (attempt {}/{}): {}", evt.getId(), nextRetry, maxRetries, ex.getMessage());
            }
        }
        return settledCount;
    }

    public OutboxEvent retryFailedEvent(String eventId, String adminActor) {
        OutboxEvent evt = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND.value(), "OUTBOX_EVENT_NOT_FOUND", "Outbox event not found"));
        if (!"FAILED".equals(evt.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "EVENT_NOT_FAILED", "Only FAILED events can be manually retried");
        }
        evt.setStatus("PENDING");
        evt.setRetryCount(0);
        evt.setLastError(null);
        evt.setProcessingStartedAt(null);
        OutboxEvent saved = outboxEventRepository.save(evt);

        auditService.log(evt.getTenantId(), adminActor, "ADMIN", "SETTLEMENT_MANUAL_RETRY",
                "OUTBOX_EVENT", evt.getId(), "SUCCESS", "Reset FAILED settlement to PENDING", evt.getAggregateId());
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
