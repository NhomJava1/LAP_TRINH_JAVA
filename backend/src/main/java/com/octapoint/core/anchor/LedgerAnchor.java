package com.octapoint.core.anchor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ledger_anchors")
public class LedgerAnchor {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "entry_count", nullable = false)
    private Long entryCount;

    @Column(name = "batch_sha256_hash", nullable = false, length = 64)
    private String batchSha256Hash;

    @Column(name = "sui_tx_digest", length = 128)
    private String suiTxDigest;

    @Column(name = "walrus_blob_id", length = 128)
    private String walrusBlobId;

    @Column(nullable = false, length = 30)
    private String status;

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

    public Long getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(Long entryCount) {
        this.entryCount = entryCount;
    }

    public String getBatchSha256Hash() {
        return batchSha256Hash;
    }

    public void setBatchSha256Hash(String batchSha256Hash) {
        this.batchSha256Hash = batchSha256Hash;
    }

    public String getSuiTxDigest() {
        return suiTxDigest;
    }

    public void setSuiTxDigest(String suiTxDigest) {
        this.suiTxDigest = suiTxDigest;
    }

    public String getWalrusBlobId() {
        return walrusBlobId;
    }

    public void setWalrusBlobId(String walrusBlobId) {
        this.walrusBlobId = walrusBlobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
