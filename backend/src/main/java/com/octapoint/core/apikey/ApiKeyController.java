package com.octapoint.core.apikey;

import com.octapoint.core.security.PortalPrincipal;
import com.octapoint.core.shared.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    public ResponseEntity<?> createApiKey(HttpServletRequest request) {
        Object attr = request.getAttribute(PortalPrincipal.REQUEST_ATTR);
        if (!(attr instanceof PortalPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED.value(), "AUTH_REQUIRED", "Admin authentication required");
        }
        if (!"ADMIN".equals(principal.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN.value(), "FORBIDDEN_ADMIN_ONLY", "Admin role required");
        }

        try {
            String keyId = "ak_" + UUID.randomUUID().toString();

            byte[] secretBytes = new byte[32];
            SecureRandom.getInstanceStrong().nextBytes(secretBytes);
            String rawSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

            apiKeyService.encrypt(rawSecret);

            return ResponseEntity.ok(Map.of(
                    "key_id", keyId,
                    "secret", rawSecret,
                    "created_at", Instant.now().toString()));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to create API key"));
        }
    }
}