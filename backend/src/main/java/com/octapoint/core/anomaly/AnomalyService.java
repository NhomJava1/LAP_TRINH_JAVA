package com.octapoint.core.anomaly;

import com.octapoint.core.ledger.LedgerEntryRepository;
import com.octapoint.core.metrics.OctaPointMetrics;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AnomalyService {

    private static final long HIGH_SINGLE_ISSUE_THRESHOLD = 5_000L;
    private static final long HIGH_FREQUENCY_LIMIT_PER_MIN = 15L;

    private final AnomalyAlertRepository anomalyAlertRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OctaPointMetrics metrics;

    public AnomalyService(
            AnomalyAlertRepository anomalyAlertRepository,
            LedgerEntryRepository ledgerEntryRepository,
            OctaPointMetrics metrics) {
        this.anomalyAlertRepository = anomalyAlertRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.metrics = metrics;
    }

    public void evaluateTransaction(String tenantId, String customerId, String txId, String type, long points) {
        if ("ISSUE".equals(type) && points >= HIGH_SINGLE_ISSUE_THRESHOLD) {
            saveAlert(
                    tenantId,
                    customerId,
                    txId,
                    "HIGH_SINGLE_ISSUE",
                    "HIGH",
                    "Single issue of " + points + " points exceeded threshold (" + HIGH_SINGLE_ISSUE_THRESHOLD + ")"
            );
        }

        Instant oneMinuteAgo = Instant.now().minusSeconds(60);
        long recentCount = ledgerEntryRepository.countByTenantIdAndCustomerIdAndCreatedAtAfter(
                tenantId, customerId, oneMinuteAgo);
        if (recentCount >= HIGH_FREQUENCY_LIMIT_PER_MIN) {
            saveAlert(
                    tenantId,
                    customerId,
                    txId,
                    "HIGH_FREQUENCY_CUSTOMER",
                    "MEDIUM",
                    "Customer " + customerId + " reached " + recentCount + " transactions within 60 seconds"
            );
        }
    }

    private void saveAlert(
            String tenantId,
            String customerId,
            String txId,
            String ruleCode,
            String severity,
            String description) {
        AnomalyAlert alert = new AnomalyAlert();
        alert.setId("anom_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        alert.setTenantId(tenantId);
        alert.setCustomerId(customerId);
        alert.setTransactionId(txId);
        alert.setRuleCode(ruleCode);
        alert.setSeverity(severity);
        alert.setDescription(description);
        anomalyAlertRepository.save(alert);
        metrics.recordAnomalyDetected();
    }

    public List<AnomalyAlert> listByTenant(String tenantId) {
        return anomalyAlertRepository.findTop50ByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    public List<AnomalyAlert> listAll() {
        return anomalyAlertRepository.findTop50ByOrderByCreatedAtDesc();
    }
}
