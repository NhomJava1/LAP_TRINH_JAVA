package com.octapoint.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OctaPointMetrics {

    private final Counter creditsIssued;
    private final Counter creditsRedeemed;
    private final Counter hmacRejected;
    private final Counter settlementsSettled;
    private final Counter settlementsFailed;
    private final Counter anomaliesDetected;

    public OctaPointMetrics(MeterRegistry registry) {
        this.creditsIssued = Counter.builder("octapoint.credits.issued")
                .description("Total successful credit issue operations")
                .register(registry);
        this.creditsRedeemed = Counter.builder("octapoint.credits.redeemed")
                .description("Total successful credit redeem operations")
                .register(registry);
        this.hmacRejected = Counter.builder("octapoint.hmac.rejected")
                .description("Total rejected HMAC merchant requests")
                .register(registry);
        this.settlementsSettled = Counter.builder("octapoint.settlement.settled")
                .description("Total settled outbox events")
                .register(registry);
        this.settlementsFailed = Counter.builder("octapoint.settlement.failed")
                .description("Total failed outbox settlement events")
                .register(registry);
        this.anomaliesDetected = Counter.builder("octapoint.anomalies.detected")
                .description("Total anomaly alerts raised")
                .register(registry);
    }

    public void recordIssue() {
        creditsIssued.increment();
    }

    public void recordRedeem() {
        creditsRedeemed.increment();
    }

    public void recordHmacRejected() {
        hmacRejected.increment();
    }

    public void recordSettlementSettled() {
        settlementsSettled.increment();
    }

    public void recordSettlementFailed() {
        settlementsFailed.increment();
    }

    public void recordAnomalyDetected() {
        anomaliesDetected.increment();
    }

    public Map<String, Object> summary() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("creditsIssuedCount", (long) creditsIssued.count());
        map.put("creditsRedeemedCount", (long) creditsRedeemed.count());
        map.put("hmacRejectedCount", (long) hmacRejected.count());
        map.put("settlementSettledCount", (long) settlementsSettled.count());
        map.put("settlementFailedCount", (long) settlementsFailed.count());
        map.put("anomaliesDetectedCount", (long) anomaliesDetected.count());
        return map;
    }
}
