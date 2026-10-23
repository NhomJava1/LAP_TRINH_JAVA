package com.octapoint.core.apikey;

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
    public ResponseEntity<?> createApiKey() {
        try {
            String keyId = "ak_" + UUID.randomUUID().toString();
            
            byte[] secretBytes = new byte[32];
            SecureRandom.getInstanceStrong().nextBytes(secretBytes);
            String rawSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

            ApiKeyService.EncryptedResult encryptedResult = apiKeyService.encrypt(rawSecret);

            return ResponseEntity.ok(Map.of(
                    "key_id", keyId,
                    "secret", rawSecret,
                    "created_at", Instant.now().toString()
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}