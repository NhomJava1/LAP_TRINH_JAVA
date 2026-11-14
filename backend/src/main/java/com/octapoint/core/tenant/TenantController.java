package com.octapoint.core.tenant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/merchants")
public class TenantController {

    private final TenantRepository tenantRepository;

    public TenantController(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerMerchant(@RequestBody Tenant request) {
        // Kiểm tra email đã tồn tại chưa
        if (tenantRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Email đã được sử dụng!");
        }

        // Tạo tenant mới với trạng thái PENDING
        Tenant newTenant = new Tenant();
        newTenant.setBusinessName(request.getBusinessName());
        newTenant.setEmail(request.getEmail());
        newTenant.setPhone(request.getPhone());
        newTenant.setStatus(TenantStatus.PENDING);

        tenantRepository.save(newTenant);

        return ResponseEntity.ok("Đăng ký thành công! Vui lòng chờ Admin duyệt.");
    }
}