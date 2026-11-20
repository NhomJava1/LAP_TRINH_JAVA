package com.octapoint.core.portal;

import com.octapoint.core.audit.AuditService;
import com.octapoint.core.security.AppUser;
import com.octapoint.core.security.AppUserRepository;
import com.octapoint.core.security.JwtService;
import com.octapoint.core.shared.ApiException;
import com.octapoint.core.tenant.Tenant;
import com.octapoint.core.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
public class AuthController {

    private final TenantService tenantService;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthController(
            TenantService tenantService,
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditService auditService) {
        this.tenantService = tenantService;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @PostMapping({ "/api/portal/auth/register", "/api/v1/merchants/register" })
    @Transactional
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody RegisterRequest body,
            HttpServletRequest request) {
        String businessName = body != null ? firstNonBlank(body.businessName(), body.name()) : null;
        String ownerName = body != null ? firstNonBlank(body.name(), businessName) : null;
        String email = body != null ? body.email() : null;
        String phone = body != null ? body.phone() : null;
        String password = (body != null && body.password() != null) ? body.password().trim() : null;

        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_EMAIL", "Email is required");
        }
        if (password == null || password.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_PASSWORD",
                    "Password must be at least 8 characters");
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (appUserRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT.value(), "EMAIL_ALREADY_EXISTS", "Email already registered");
        }

        Tenant tenant = tenantService.registerTenant(businessName, normalizedEmail, phone);

        AppUser user = new AppUser();
        user.setId("usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        user.setTenantId(tenant.getId());
        user.setName(ownerName != null ? ownerName : tenant.getName());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("MERCHANT");
        appUserRepository.save(user);

        auditService.log(tenant.getId(), normalizedEmail, "MERCHANT", "TENANT_REGISTER",
                "TENANT", tenant.getId(), "SUCCESS", "Registered tenant " + tenant.getName(), null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("merchantId", tenant.getId());
        response.put("tenantId", tenant.getId());
        response.put("businessName", tenant.getName());
        response.put("email", tenant.getEmail());
        response.put("status", tenant.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/api/portal/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest body) {
        String email = body != null ? body.email() : null;
        String password = body != null ? body.password() : null;
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "MISSING_CREDENTIALS",
                    "Email and password are required");
        }

        String normalizedEmail = email.trim().toLowerCase();
        AppUser user = appUserRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    auditService.log(null, normalizedEmail, "UNKNOWN", "PORTAL_LOGIN", "USER", normalizedEmail,
                            "REJECTED", "Unknown email", null);
                    return new ApiException(HttpStatus.UNAUTHORIZED.value(), "INVALID_CREDENTIALS",
                            "Invalid email or password");
                });

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            auditService.log(user.getTenantId(), normalizedEmail, user.getRole(), "PORTAL_LOGIN", "USER", user.getId(),
                    "REJECTED", "Wrong password", null);
            throw new ApiException(HttpStatus.UNAUTHORIZED.value(), "INVALID_CREDENTIALS", "Invalid email or password");
        }

        String token = jwtService.generateToken(user);
        String tenantName = null;
        String tenantStatus = null;
        if (user.getTenantId() != null) {
            Tenant tenant = tenantService.getTenant(user.getTenantId());
            tenantName = tenant.getName();
            tenantStatus = tenant.getStatus();
        }

        auditService.log(user.getTenantId(), normalizedEmail, user.getRole(), "PORTAL_LOGIN", "USER", user.getId(),
                "SUCCESS", "Logged in to portal", null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", token);
        response.put("role", user.getRole());
        response.put("email", user.getEmail());
        response.put("name", user.getName());
        response.put("tenantId", user.getTenantId());
        response.put("tenantName", tenantName);
        response.put("tenantStatus", tenantStatus);
        return ResponseEntity.ok(response);
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return (fallback != null && !fallback.isBlank()) ? fallback.trim() : null;
    }

    public record RegisterRequest(String businessName, String name, String email, String phone, String password) {
    }

    public record LoginRequest(String email, String password) {
    }
}
