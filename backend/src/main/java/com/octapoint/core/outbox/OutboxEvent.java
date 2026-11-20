package com.octapoint.core.outbox;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent implements Persistable<String> {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "customer_id", length = 128)
    private String customerId;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, length = 50)
    private String status = "PENDING";

    @Column(name = "settlement_mode", nullable = false, length = 20)
    private String settlementMode = "MOCK";

    @Column(name = "chain_seq")
    private Long chainSeq;

    @Column(name = "order_key")
    private Long orderKey;

    @Column(name = "tx_digest", length = 128)
    private String txDigest;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "gas_used_mist", nullable = false)
    private Long gasUsedMist = 0L;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Transient
    private boolean newEntity = true;

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.newEntity = false;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (gasUsedMist == null) {
            gasUsedMist = 0L;
        }
    }

    @Override
    @JsonIgnore
    public boolean isNew() {
        return newEntity;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSettlementMode() {
        return settlementMode;
    }

    public void setSettlementMode(String settlementMode) {
        this.settlementMode = settlementMode;
    }

    public Long getChainSeq() {
        return chainSeq;
    }

    public void setChainSeq(Long chainSeq) {
        this.chainSeq = chainSeq;
    }

    public Long getOrderKey() {
        return orderKey;
    }

    public void setOrderKey(Long orderKey) {
        this.orderKey = orderKey;
    }

    public String getTxDigest() {
        return txDigest;
    }

    public void setTxDigest(String txDigest) {
        this.txDigest = txDigest;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Long getGasUsedMist() {
        return gasUsedMist;
    }

    public void setGasUsedMist(Long gasUsedMist) {
        this.gasUsedMist = gasUsedMist;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getProcessingStartedAt() {
        return processingStartedAt;
    }

    public void setProcessingStartedAt(Instant processingStartedAt) {
        this.processingStartedAt = processingStartedAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(Instant settledAt) {
        this.settledAt = settledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
