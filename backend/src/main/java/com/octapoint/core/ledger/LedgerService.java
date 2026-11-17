package com.octapoint.core.ledger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.octapoint.core.anomaly.AnomalyService;
import com.octapoint.core.metrics.OctaPointMetrics;
import com.octapoint.core.outbox.OutboxEvent;
import com.octapoint.core.outbox.OutboxEventRepository;
import com.octapoint.core.shared.ApiException;
import com.octapoint.core.shared.HmacCodec;
import com.octapoint.core.tenant.Tenant;
import com.octapoint.core.tenant.TenantRule;
import com.octapoint.core.tenant.TenantService;
import com.octapoint.core.tenant.TenantStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LedgerService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final CustomerRepository customerRepository;
    private final CustomerEnsureService customerEnsureService;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final TenantService tenantService;
    private final AnomalyService anomalyService;
    private final OctaPointMetrics metrics;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final String settlementMode;

    public LedgerService(
            CustomerRepository customerRepository,
            CustomerEnsureService customerEnsureService,
            LedgerEntryRepository ledgerEntryRepository,
            OutboxEventRepository outboxEventRepository,
            IdempotencyRecordRepository idempotencyRecordRepository,
            TenantService tenantService,
            AnomalyService anomalyService,
            OctaPointMetrics metrics,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            @Value("${app.settlement.mode:LIVE}") String settlementMode) {
        this.customerRepository = customerRepository;
        this.customerEnsureService = customerEnsureService;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.tenantService = tenantService;
        this.anomalyService = anomalyService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.settlementMode = settlementMode != null ? settlementMode.toUpperCase() : "MOCK";
    }

    public Map<String, Object> issue(
            String tenantId,
            String customerId,
            String orderId,
            Long amountVnd,
            String idempotencyKey,
            String actor) {
        if (customerId == null || customerId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_CUSTOMER_ID", "customerId is required");
        }
        if (amountVnd == null || amountVnd <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_AMOUNT", "amountVnd must be a positive integer");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "MISSING_IDEMPOTENCY_KEY", "Idempotency-Key header is required");
        }

        Tenant tenant = requireApprovedTenant(tenantId);
        String cleanCustomerId = customerId.trim();
        String cleanOrderId = normalizeOrderId(orderId);
        String scopedIdemKey = tenant.getId() + ":ISSUE:" + idempotencyKey.trim();
        String requestHash = HmacCodec.sha256Hex("ISSUE|" + cleanCustomerId + "|" + cleanOrderId + "|" + amountVnd);

        Optional<IdempotencyRecord> quickCheck = idempotencyRecordRepository.findById(scopedIdemKey);
        if (quickCheck.isPresent()) {
            return handleExistingIdempotency(quickCheck.get(), requestHash);
        }

        String compositeId = compositeCustomerId(tenant.getId(), cleanCustomerId);
        customerEnsureService.ensureExists(compositeId, tenant.getId(), cleanCustomerId);

        IssueTxOutcome outcome = transactionTemplate.execute(status -> {
            Optional<IdempotencyRecord> existingIdem = idempotencyRecordRepository.findById(scopedIdemKey);
            if (existingIdem.isPresent()) {
                return new IssueTxOutcome(handleExistingIdempotency(existingIdem.get(), requestHash), true);
            }

            TenantRule rule = tenantService.getOrCreateRule(tenant.getId());
            long basePoints = amountVnd / rule.getEarnVndPerPoint();
            if (basePoints <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST.value(), "AMOUNT_TOO_SMALL",
                        "Order amount does not yield at least 1 point under current rule");
            }

            Customer customer = customerRepository.findByIdForUpdate(compositeId)
                    .orElseThrow(() -> new IllegalStateException("Customer row missing after ensureExists"));

            long lifetime = customer.getLifetimeEarnedPoints() != null ? customer.getLifetimeEarnedPoints() : 0L;
            String tier = resolveTier(lifetime, rule);
            BigDecimal multiplier = resolveMultiplier(tier, rule);
            long points = Math.max(1L, BigDecimal.valueOf(basePoints).multiply(multiplier).longValue());

            long nextSeq = customer.getChainSeq() + 1;
            long newBalance = customer.getBalance() + points;
            customer.setChainSeq(nextSeq);
            customer.setBalance(newBalance);
            customer.setLifetimeEarnedPoints(lifetime + points);
            customerRepository.save(customer);

            Instant expiresAt = Instant.now().plus(Duration.ofDays(
                    rule.getPointExpiryDays() != null ? rule.getPointExpiryDays() : 365));

            String txId = "tx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            LedgerEntry entry = new LedgerEntry();
            entry.setId(txId);
            entry.setTenantId(tenant.getId());
            entry.setCustomerId(cleanCustomerId);
            entry.setType("ISSUE");
            entry.setAmount(BigDecimal.valueOf(points));
            entry.setAmountVnd(amountVnd);
            entry.setReferenceId(cleanOrderId);
            entry.setChainSeq(nextSeq);
            entry.setAppliedTier(tier);
            entry.setExpiresAt(expiresAt);
            ledgerEntryRepository.save(entry);

            String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            OutboxEvent outbox = new OutboxEvent();
            outbox.setId(eventId);
            outbox.setTenantId(tenant.getId());
            outbox.setCustomerId(cleanCustomerId);
            outbox.setAggregateType("LEDGER_ENTRY");
            outbox.setAggregateId(txId);
            outbox.setEventType("ISSUE");
            outbox.setStatus("PENDING");
            outbox.setSettlementMode(settlementMode);
            outbox.setChainSeq(nextSeq);
            outbox.setOrderKey(nextSeq);
            outbox.setPayload(serializePayload(Map.of(
                    "eventId", eventId,
                    "transactionId", txId,
                    "tenantId", tenant.getId(),
                    "customerId", cleanCustomerId,
                    "orderId", cleanOrderId,
                    "points", points,
                    "chainSeq", nextSeq
            )));
            outboxEventRepository.save(outbox);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("transactionId", txId);
            response.put("eventId", eventId);
            response.put("customerId", cleanCustomerId);
            response.put("orderId", cleanOrderId);
            response.put("amountVnd", amountVnd);
            response.put("pointsIssued", points);
            response.put("tier", tier);
            response.put("expiresAt", expiresAt.toString());
            response.put("balance", newBalance);
            response.put("chainSeq", nextSeq);
            response.put("status", "COMPLETED");
            response.put("settlementStatus", "PENDING");
            response.put("settlementMode", settlementMode);
            response.put("createdAt", Instant.now().toString());

            saveIdempotencyRecord(scopedIdemKey, tenant.getId(), requestHash, response, HttpStatus.OK.value());
            return new IssueTxOutcome(response, false);
        });

        if (outcome != null && !outcome.fromIdempotency()) {
            metrics.recordIssue();
            long pts = ((Number) outcome.response().get("pointsIssued")).longValue();
            String txId = String.valueOf(outcome.response().get("transactionId"));
            anomalyService.evaluateTransaction(tenant.getId(), cleanCustomerId, txId, "ISSUE", pts);
        }
        return outcome != null ? outcome.response() : Map.of();
    }

    public Map<String, Object> redeem(
            String tenantId,
            String customerId,
            String orderId,
            Long pointsToRedeem,
            String idempotencyKey,
            String actor) {
        if (customerId == null || customerId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_CUSTOMER_ID", "customerId is required");
        }
        if (pointsToRedeem == null || pointsToRedeem <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_POINTS", "pointsToRedeem must be a positive integer");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "MISSING_IDEMPOTENCY_KEY", "Idempotency-Key header is required");
        }

        Tenant tenant = requireApprovedTenant(tenantId);
        String cleanCustomerId = customerId.trim();
        String cleanOrderId = normalizeOrderId(orderId);
        String scopedIdemKey = tenant.getId() + ":REDEEM:" + idempotencyKey.trim();
        String requestHash = HmacCodec.sha256Hex("REDEEM|" + cleanCustomerId + "|" + cleanOrderId + "|" + pointsToRedeem);

        IssueTxOutcome outcome = transactionTemplate.execute(status -> redeemInTx(
                tenant, cleanCustomerId, cleanOrderId, pointsToRedeem, scopedIdemKey, requestHash));
        if (outcome != null && !outcome.fromIdempotency()) {
            metrics.recordRedeem();
            String txId = String.valueOf(outcome.response().get("transactionId"));
            anomalyService.evaluateTransaction(tenant.getId(), cleanCustomerId, txId, "REDEEM", pointsToRedeem);
        }
        return outcome != null ? outcome.response() : Map.of();
    }

    private IssueTxOutcome redeemInTx(
            Tenant tenant,
            String cleanCustomerId,
            String cleanOrderId,
            Long pointsToRedeem,
            String scopedIdemKey,
            String requestHash) {
        Optional<IdempotencyRecord> existingIdem = idempotencyRecordRepository.findById(scopedIdemKey);
        if (existingIdem.isPresent()) {
            return new IssueTxOutcome(handleExistingIdempotency(existingIdem.get(), requestHash), true);
        }

        String compositeId = compositeCustomerId(tenant.getId(), cleanCustomerId);
        Optional<Customer> customerOpt = customerRepository.findByIdForUpdate(compositeId);
        if (customerOpt.isEmpty() || customerOpt.get().getBalance() < pointsToRedeem) {
            long currentBalance = customerOpt.map(Customer::getBalance).orElse(0L);
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY.value(), "INSUFFICIENT_BALANCE",
                    "Insufficient credit balance (available=" + currentBalance + ", requested=" + pointsToRedeem + ")");
        }

        Customer customer = customerOpt.get();
        long nextSeq = customer.getChainSeq() + 1;
        long remainingBalance = customer.getBalance() - pointsToRedeem;
        customer.setChainSeq(nextSeq);
        customer.setBalance(remainingBalance);
        customerRepository.save(customer);

        String txId = "tx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        LedgerEntry entry = new LedgerEntry();
        entry.setId(txId);
        entry.setTenantId(tenant.getId());
        entry.setCustomerId(cleanCustomerId);
        entry.setType("REDEEM");
        entry.setAmount(BigDecimal.valueOf(pointsToRedeem));
        entry.setReferenceId(cleanOrderId);
        entry.setChainSeq(nextSeq);
        ledgerEntryRepository.save(entry);

        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        OutboxEvent outbox = new OutboxEvent();
        outbox.setId(eventId);
        outbox.setTenantId(tenant.getId());
        outbox.setCustomerId(cleanCustomerId);
        outbox.setAggregateType("LEDGER_ENTRY");
        outbox.setAggregateId(txId);
        outbox.setEventType("REDEEM");
        outbox.setStatus("PENDING");
        outbox.setSettlementMode(settlementMode);
        outbox.setChainSeq(nextSeq);
        outbox.setOrderKey(nextSeq);
        outbox.setPayload(serializePayload(Map.of(
                "eventId", eventId,
                "transactionId", txId,
                "tenantId", tenant.getId(),
                "customerId", cleanCustomerId,
                "orderId", cleanOrderId,
                "points", pointsToRedeem,
                "chainSeq", nextSeq
        )));
        outboxEventRepository.save(outbox);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transactionId", txId);
        response.put("eventId", eventId);
        response.put("customerId", cleanCustomerId);
        response.put("orderId", cleanOrderId);
        response.put("pointsRedeemed", pointsToRedeem);
        response.put("remainingBalance", remainingBalance);
        response.put("balance", remainingBalance);
        response.put("chainSeq", nextSeq);
        response.put("status", "COMPLETED");
        response.put("settlementStatus", "PENDING");
        response.put("settlementMode", settlementMode);
        response.put("createdAt", Instant.now().toString());

        saveIdempotencyRecord(scopedIdemKey, tenant.getId(), requestHash, response, HttpStatus.OK.value());
        return new IssueTxOutcome(response, false);
    }

    public Map<String, Object> getBalance(String tenantId, String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_CUSTOMER_ID", "customerId is required");
        }
        String cleanCustomerId = customerId.trim();
        Optional<Customer> customerOpt = customerRepository.findById(compositeCustomerId(tenantId, cleanCustomerId));
        long balance = customerOpt.map(Customer::getBalance).orElse(0L);
        long lifetime = customerOpt.map(c -> c.getLifetimeEarnedPoints() != null ? c.getLifetimeEarnedPoints() : 0L).orElse(0L);
        long chainSeq = customerOpt.map(Customer::getChainSeq).orElse(-1L);
        TenantRule rule = tenantService.getOrCreateRule(tenantId);
        String tier = resolveTier(lifetime, rule);

        List<LedgerEntry> recent = ledgerEntryRepository.findTop100ByTenantIdAndCustomerIdOrderByCreatedAtDesc(tenantId, cleanCustomerId);
        String latestSettlementStatus = "SETTLED";
        String latestMode = settlementMode;
        String latestDigest = null;
        if (!recent.isEmpty()) {
            Optional<OutboxEvent> evtOpt = outboxEventRepository.findByAggregateId(recent.get(0).getId());
            if (evtOpt.isPresent()) {
                latestSettlementStatus = evtOpt.get().getStatus();
                latestMode = evtOpt.get().getSettlementMode();
                latestDigest = evtOpt.get().getTxDigest();
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("customerId", cleanCustomerId);
        res.put("balance", balance);
        res.put("lifetimeEarnedPoints", lifetime);
        res.put("tier", tier);
        res.put("chainSeq", chainSeq);
        res.put("settlementStatus", latestSettlementStatus);
        res.put("settlementMode", latestMode);
        res.put("txDigest", latestDigest);
        return res;
    }

    public List<Map<String, Object>> getHistory(String tenantId, String customerId) {
        List<LedgerEntry> entries = (customerId != null && !customerId.isBlank())
                ? ledgerEntryRepository.findTop100ByTenantIdAndCustomerIdOrderByCreatedAtDesc(tenantId, customerId.trim())
                : ledgerEntryRepository.findTop100ByTenantIdOrderByCreatedAtDesc(tenantId);

        if (entries.isEmpty()) {
            return List.of();
        }

        List<String> txIds = entries.stream().map(LedgerEntry::getId).toList();
        Map<String, OutboxEvent> outboxByTxId = outboxEventRepository.findByAggregateIdIn(txIds).stream()
                .collect(Collectors.toMap(OutboxEvent::getAggregateId, e -> e, (a, b) -> a));

        return entries.stream().map(entry -> {
            OutboxEvent evt = outboxByTxId.get(entry.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("transactionId", entry.getId());
            item.put("eventId", evt != null ? evt.getId() : null);
            item.put("customerId", entry.getCustomerId());
            item.put("type", entry.getType());
            item.put("points", entry.getAmount() != null ? entry.getAmount().longValue() : 0L);
            item.put("amountVnd", entry.getAmountVnd());
            item.put("orderId", entry.getReferenceId());
            item.put("chainSeq", entry.getChainSeq());
            item.put("appliedTier", entry.getAppliedTier());
            item.put("expiresAt", entry.getExpiresAt() != null ? entry.getExpiresAt().toString() : null);
            item.put("settlementStatus", evt != null ? evt.getStatus() : "PENDING");
            item.put("settlementMode", evt != null ? evt.getSettlementMode() : settlementMode);
            item.put("txDigest", evt != null ? evt.getTxDigest() : null);
            item.put("lastError", evt != null ? evt.getLastError() : null);
            item.put("createdAt", entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : null);
            item.put("settledAt", (evt != null && evt.getSettledAt() != null) ? evt.getSettledAt().toString() : null);
            return item;
        }).toList();
    }

    public List<Map<String, Object>> listCustomers(String tenantId) {
        TenantRule rule = tenantService.getOrCreateRule(tenantId);
        return customerRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(c -> {
            long lifetime = c.getLifetimeEarnedPoints() != null ? c.getLifetimeEarnedPoints() : 0L;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("customerId", c.getExternalCustomerId() != null ? c.getExternalCustomerId() : c.getId());
            row.put("balance", c.getBalance());
            row.put("lifetimeEarnedPoints", lifetime);
            row.put("tier", resolveTier(lifetime, rule));
            row.put("chainSeq", c.getChainSeq());
            row.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
            return row;
        }).toList();
    }

    public Map<String, Object> getTenantDashboardStats(String tenantId) {
        List<Customer> customers = customerRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        long circulatingPoints = customers.stream().mapToLong(Customer::getBalance).sum();
        List<LedgerEntry> recent = ledgerEntryRepository.findTop100ByTenantIdOrderByCreatedAtDesc(tenantId);
        long totalIssued = recent.stream()
                .filter(e -> "ISSUE".equals(e.getType()))
                .mapToLong(e -> e.getAmount().longValue())
                .sum();
        long totalRedeemed = recent.stream()
                .filter(e -> "REDEEM".equals(e.getType()))
                .mapToLong(e -> e.getAmount().longValue())
                .sum();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("circulatingPoints", circulatingPoints);
        stats.put("totalCustomers", (long) customers.size());
        stats.put("totalIssuedPoints", totalIssued);
        stats.put("totalRedeemedPoints", totalRedeemed);
        stats.put("pendingSettlements", outboxEventRepository.countByTenantIdAndStatus(tenantId, "PENDING"));
        stats.put("settledSettlements", outboxEventRepository.countByTenantIdAndStatus(tenantId, "SETTLED"));
        stats.put("failedSettlements", outboxEventRepository.countByTenantIdAndStatus(tenantId, "FAILED"));
        stats.put("pausedGasSettlements", outboxEventRepository.countByTenantIdAndStatus(tenantId, "PAUSED_GAS_QUOTA"));
        stats.put("settlementMode", settlementMode);
        return stats;
    }

    public Map<String, Object> getTenantAnalytics(String tenantId) {
        List<LedgerEntry> entries = ledgerEntryRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        List<Customer> customers = customerRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        return computeAnalytics(entries, customers);
    }

    public Map<String, Object> getGlobalAnalytics() {
        List<LedgerEntry> entries = ledgerEntryRepository.findAllByOrderByCreatedAtAsc();
        List<Customer> customers = customerRepository.findAll();
        return computeAnalytics(entries, customers);
    }

    private Map<String, Object> computeAnalytics(List<LedgerEntry> entries, List<Customer> customers) {
        long issuedPts = 0L;
        long redeemedPts = 0L;
        long issueTxCount = 0L;
        long redeemTxCount = 0L;

        Map<String, long[]> byDay = new TreeMap<>();
        for (LedgerEntry entry : entries) {
            long pts = entry.getAmount() != null ? entry.getAmount().longValue() : 0L;
            String day = entry.getCreatedAt() != null ? DAY_FMT.format(entry.getCreatedAt()) : DAY_FMT.format(Instant.now());
            long[] bucket = byDay.computeIfAbsent(day, k -> new long[3]); // [issued, redeemed, count]
            bucket[2]++;
            if ("ISSUE".equals(entry.getType())) {
                issuedPts += pts;
                issueTxCount++;
                bucket[0] += pts;
            } else if ("REDEEM".equals(entry.getType())) {
                redeemedPts += pts;
                redeemTxCount++;
                bucket[1] += pts;
            }
        }

        double redemptionRate = issuedPts > 0 ? Math.round((redeemedPts * 10000.0) / issuedPts) / 100.0 : 0.0;

        List<Map<String, Object>> daily = byDay.entrySet().stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", e.getKey());
            row.put("issuedPoints", e.getValue()[0]);
            row.put("redeemedPoints", e.getValue()[1]);
            row.put("transactionCount", e.getValue()[2]);
            return row;
        }).toList();

        List<Map<String, Object>> topCustomers = customers.stream()
                .sorted(Comparator.comparingLong(Customer::getBalance).reversed())
                .limit(10)
                .map(c -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("customerId", c.getExternalCustomerId() != null ? c.getExternalCustomerId() : c.getId());
                    row.put("balance", c.getBalance());
                    row.put("lifetimeEarnedPoints", c.getLifetimeEarnedPoints() != null ? c.getLifetimeEarnedPoints() : 0L);
                    return row;
                })
                .toList();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("totalTransactions", (long) entries.size());
        res.put("issueTransactions", issueTxCount);
        res.put("redeemTransactions", redeemTxCount);
        res.put("totalIssuedPoints", issuedPts);
        res.put("totalRedeemedPoints", redeemedPts);
        res.put("redemptionRatePercent", redemptionRate);
        res.put("daily", daily);
        res.put("topCustomers", topCustomers);
        return res;
    }

    public long countTotalTransactions() {
        return ledgerEntryRepository.count();
    }

    public long countRecentTenantTransactions(String tenantId, Instant since) {
        return ledgerEntryRepository.countByTenantIdAndCreatedAtAfter(tenantId, since);
    }

    private String resolveTier(long lifetimeEarned, TenantRule rule) {
        long goldThreshold = rule.getGoldThresholdPoints() != null ? rule.getGoldThresholdPoints() : 2000L;
        long silverThreshold = rule.getSilverThresholdPoints() != null ? rule.getSilverThresholdPoints() : 500L;
        if (lifetimeEarned >= goldThreshold) {
            return "GOLD";
        }
        if (lifetimeEarned >= silverThreshold) {
            return "SILVER";
        }
        return "BRONZE";
    }

    private BigDecimal resolveMultiplier(String tier, TenantRule rule) {
        if ("GOLD".equals(tier)) {
            return rule.getGoldMultiplier() != null ? rule.getGoldMultiplier() : new BigDecimal("1.50");
        }
        if ("SILVER".equals(tier)) {
            return rule.getSilverMultiplier() != null ? rule.getSilverMultiplier() : new BigDecimal("1.20");
        }
        return BigDecimal.ONE;
    }

    private Tenant requireApprovedTenant(String tenantId) {
        Tenant tenant = tenantService.getTenant(tenantId);
        if (tenant.getStatusEnum() != TenantStatus.APPROVED) {
            throw new ApiException(HttpStatus.FORBIDDEN.value(), "TENANT_NOT_APPROVED",
                    "Tenant status must be APPROVED (current=" + tenant.getStatus() + ")");
        }
        return tenant;
    }

    private Map<String, Object> handleExistingIdempotency(IdempotencyRecord record, String requestHash) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new ApiException(HttpStatus.CONFLICT.value(), "IDEMPOTENCY_KEY_CONFLICT",
                    "Idempotency-Key reused with different request payload");
        }
        try {
            return objectMapper.readValue(record.getResponseBody(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize stored idempotency response", e);
        }
    }

    private void saveIdempotencyRecord(
            String scopedIdemKey,
            String tenantId,
            String requestHash,
            Map<String, Object> response,
            int statusCode) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey(scopedIdemKey);
        record.setTenantId(tenantId);
        record.setRequestHash(requestHash);
        record.setResponseBody(serializePayload(response));
        record.setStatusCode(statusCode);
        idempotencyRecordRepository.save(record);
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JSON payload", e);
        }
    }

    private String compositeCustomerId(String tenantId, String customerId) {
        return tenantId + ":" + customerId;
    }

    private String normalizeOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return "ord_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        String trimmed = orderId.trim();
        return trimmed.length() <= 64 ? trimmed : trimmed.substring(0, 64);
    }

    protected record IssueTxOutcome(Map<String, Object> response, boolean fromIdempotency) {
    }
}
