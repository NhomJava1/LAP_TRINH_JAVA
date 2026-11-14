package com.octapoint.core.tenant;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenants")
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String businessName;

    @Column(unique = true, nullable = false)
    private String email;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantStatus status = TenantStatus.PENDING;

    private LocalDateTime createdAt = LocalDateTime.now();

    // Getters và Setters (Bạn có thể dùng @Data của Lombok nếu dự án có cài)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public TenantStatus getStatus() { return status; }
    public void setStatus(TenantStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

enum TenantStatus {
    PENDING, APPROVED, REJECTED, LOCKED
}