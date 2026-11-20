package com.octapoint.core.reconcile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "reconciliation_reports")
public class ReconciliationReport {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "db_settled_issue_points", nullable = false)
    private Long dbSettledIssuePoints;

    @Column(name = "db_settled_redeem_points", nullable = false)
    private Long dbSettledRedeemPoints;

    @Column(name = "chain_balance_value")
    private Long chainBalanceValue;

    @Column(name = "delta_points")
    private Long deltaPoints;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String details;

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

    public Long getDbSettledIssuePoints() {
        return dbSettledIssuePoints;
    }

    public void setDbSettledIssuePoints(Long dbSettledIssuePoints) {
        this.dbSettledIssuePoints = dbSettledIssuePoints;
    }

    public Long getDbSettledRedeemPoints() {
        return dbSettledRedeemPoints;
    }

    public void setDbSettledRedeemPoints(Long dbSettledRedeemPoints) {
        this.dbSettledRedeemPoints = dbSettledRedeemPoints;
    }

    public Long getChainBalanceValue() {
        return chainBalanceValue;
    }

    public void setChainBalanceValue(Long chainBalanceValue) {
        this.chainBalanceValue = chainBalanceValue;
    }

    public Long getDeltaPoints() {
        return deltaPoints;
    }

    public void setDeltaPoints(Long deltaPoints) {
        this.deltaPoints = deltaPoints;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
