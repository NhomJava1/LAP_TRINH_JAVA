package com.octapoint.core.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

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

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void onSave() {
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
