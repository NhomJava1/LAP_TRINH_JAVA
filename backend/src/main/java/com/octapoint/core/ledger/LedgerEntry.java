package com.octapoint.core.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "customer_id", length = 128)
    private String customerId;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal amount;

    @Column(name = "amount_vnd")
    private Long amountVnd;

    @Column(name = "reference_id", length = 64)
    private String referenceId;

    @Column(name = "chain_seq")
    private Long chainSeq;

    @Column(name = "applied_tier", length = 30)
    private String appliedTier = "BRONZE";

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Long getAmountVnd() {
        return amountVnd;
    }

    public void setAmountVnd(Long amountVnd) {
        this.amountVnd = amountVnd;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public Long getChainSeq() {
        return chainSeq;
    }

    public void setChainSeq(Long chainSeq) {
        this.chainSeq = chainSeq;
    }

    public String getAppliedTier() {
        return appliedTier;
    }

    public void setAppliedTier(String appliedTier) {
        this.appliedTier = appliedTier;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
