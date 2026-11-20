package com.octapoint.core.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "tenant_rules")
public class TenantRule {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "tenant_id", nullable = false, unique = true, length = 64)
    private String tenantId;

    @Column(name = "earn_vnd_per_point", nullable = false)
    private Long earnVndPerPoint = 1000L;

    @Column(name = "redeem_value_vnd_per_point", nullable = false)
    private Long redeemValueVndPerPoint = 10L;

    @Column(name = "point_expiry_days", nullable = false)
    private Integer pointExpiryDays = 365;

    @Column(name = "silver_threshold_points", nullable = false)
    private Long silverThresholdPoints = 500L;

    @Column(name = "gold_threshold_points", nullable = false)
    private Long goldThresholdPoints = 2000L;

    @Column(name = "silver_multiplier", nullable = false, precision = 5, scale = 2)
    private BigDecimal silverMultiplier = new BigDecimal("1.20");

    @Column(name = "gold_multiplier", nullable = false, precision = 5, scale = 2)
    private BigDecimal goldMultiplier = new BigDecimal("1.50");

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void onSave() {
        if (pointExpiryDays == null) pointExpiryDays = 365;
        if (silverThresholdPoints == null) silverThresholdPoints = 500L;
        if (goldThresholdPoints == null) goldThresholdPoints = 2000L;
        if (silverMultiplier == null) silverMultiplier = new BigDecimal("1.20");
        if (goldMultiplier == null) goldMultiplier = new BigDecimal("1.50");
        updatedAt = Instant.now();
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

    public Long getEarnVndPerPoint() {
        return earnVndPerPoint;
    }

    public void setEarnVndPerPoint(Long earnVndPerPoint) {
        this.earnVndPerPoint = earnVndPerPoint;
    }

    public Long getRedeemValueVndPerPoint() {
        return redeemValueVndPerPoint;
    }

    public void setRedeemValueVndPerPoint(Long redeemValueVndPerPoint) {
        this.redeemValueVndPerPoint = redeemValueVndPerPoint;
    }

    public Integer getPointExpiryDays() {
        return pointExpiryDays;
    }

    public void setPointExpiryDays(Integer pointExpiryDays) {
        this.pointExpiryDays = pointExpiryDays;
    }

    public Long getSilverThresholdPoints() {
        return silverThresholdPoints;
    }

    public void setSilverThresholdPoints(Long silverThresholdPoints) {
        this.silverThresholdPoints = silverThresholdPoints;
    }

    public Long getGoldThresholdPoints() {
        return goldThresholdPoints;
    }

    public void setGoldThresholdPoints(Long goldThresholdPoints) {
        this.goldThresholdPoints = goldThresholdPoints;
    }

    public BigDecimal getSilverMultiplier() {
        return silverMultiplier;
    }

    public void setSilverMultiplier(BigDecimal silverMultiplier) {
        this.silverMultiplier = silverMultiplier;
    }

    public BigDecimal getGoldMultiplier() {
        return goldMultiplier;
    }

    public void setGoldMultiplier(BigDecimal goldMultiplier) {
        this.goldMultiplier = goldMultiplier;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
