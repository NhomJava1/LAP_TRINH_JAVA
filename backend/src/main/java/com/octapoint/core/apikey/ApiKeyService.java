package com.octapoint.core.apikey;

import com.octapoint.core.shared.ApiException;
import com.octapoint.core.shared.HmacCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApiKeyService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.security.api-key-master-key}")
    private String masterKey;

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    private SecretKey getSecretKey() {
        byte[] keyBytes = masterKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            try {
                keyBytes = MessageDigest.getInstance("SHA-256").digest(keyBytes);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to derive AES key", e);
            }
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    public EncryptedResult encrypt(String plainText) throws Exception {
        byte[] iv = new byte[IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), parameterSpec);

        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        return new EncryptedResult(
                Base64.getEncoder().encodeToString(cipherText),
                Base64.getEncoder().encodeToString(iv)
        );
    }

    public String decrypt(String cipherTextBase64, String ivBase64) throws Exception {
        byte[] cipherBytes = Base64.getDecoder().decode(cipherTextBase64);
        byte[] iv = Base64.getDecoder().decode(ivBase64);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), parameterSpec);

        byte[] plainBytes = cipher.doFinal(cipherBytes);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    @Transactional
    public CreatedApiKey createApiKey(String tenantId, String name) {
        return createApiKey(tenantId, name, "ISSUE,REDEEM,READ");
    }

    @Transactional
    public CreatedApiKey createApiKey(String tenantId, String name, String scopesRaw) {
        String normalizedScopes = normalizeScopes(scopesRaw);
        String keyId = "ak_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        byte[] secretBytes = new byte[32];
        SECURE_RANDOM.nextBytes(secretBytes);
        String rawSecret = "sec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        EncryptedResult encrypted;
        try {
            encrypted = encrypt(rawSecret);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "KEY_ENCRYPTION_FAILED", "Failed to encrypt API secret");
        }

        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setId(keyId);
        entity.setTenantId(tenantId);
        entity.setName((name != null && !name.isBlank()) ? name.trim() : "Merchant Key");
        entity.setKeyHash(HmacCodec.sha256Hex(rawSecret));
        entity.setEncryptedSecret(encrypted.getCipherText());
        entity.setSecretIv(encrypted.getIv());
        entity.setStatus("ACTIVE");
        entity.setScopes(normalizedScopes);
        ApiKeyEntity saved = apiKeyRepository.save(entity);

        return new CreatedApiKey(
                saved.getId(),
                saved.getId(),
                rawSecret,
                saved.getName(),
                saved.getStatus(),
                saved.getScopes(),
                saved.getCreatedAt()
        );
    }

    private String normalizeScopes(String scopesRaw) {
        if (scopesRaw == null || scopesRaw.isBlank()) {
            return "ISSUE,REDEEM,READ";
        }
        java.util.LinkedHashSet<String> valid = new java.util.LinkedHashSet<>();
        for (String part : scopesRaw.split(",")) {
            String trimmed = part.trim().toUpperCase();
            if (trimmed.isEmpty()) continue;
            if (!"ISSUE".equals(trimmed) && !"REDEEM".equals(trimmed) && !"READ".equals(trimmed)) {
                throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_SCOPE",
                        "Allowed scopes: ISSUE, REDEEM, READ");
            }
            valid.add(trimmed);
        }
        if (valid.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_SCOPE", "At least one scope is required");
        }
        return String.join(",", valid);
    }

    @Transactional
    public CreatedApiKey rotateApiKey(String tenantId, String keyId) {
        ApiKeyEntity existing = apiKeyRepository.findByIdAndTenantId(keyId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND.value(), "API_KEY_NOT_FOUND", "API key not found"));
        if ("REVOKED".equals(existing.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "API_KEY_ALREADY_REVOKED", "Cannot rotate a revoked API key");
        }
        existing.setStatus("REVOKED");
        existing.setRevokedAt(Instant.now());
        apiKeyRepository.save(existing);

        return createApiKey(tenantId, existing.getName(), existing.getScopes());
    }

    @Transactional
    public ApiKeyEntity revokeApiKey(String tenantId, String keyId) {
        ApiKeyEntity existing = apiKeyRepository.findByIdAndTenantId(keyId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND.value(), "API_KEY_NOT_FOUND", "API key not found"));
        existing.setStatus("REVOKED");
        if (existing.getRevokedAt() == null) {
            existing.setRevokedAt(Instant.now());
        }
        return apiKeyRepository.save(existing);
    }

    public List<ApiKeyEntity> listByTenant(String tenantId) {
        return apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    public long countActiveByTenant(String tenantId) {
        return apiKeyRepository.countByTenantIdAndStatus(tenantId, "ACTIVE");
    }

    public Optional<ResolvedApiKey> resolveActiveKey(String keyId) {
        return apiKeyRepository.findById(keyId)
                .filter(k -> "ACTIVE".equals(k.getStatus()))
                .filter(k -> k.getEncryptedSecret() != null && k.getSecretIv() != null)
                .map(k -> {
                    try {
                        String rawSecret = decrypt(k.getEncryptedSecret(), k.getSecretIv());
                        return new ResolvedApiKey(k.getId(), k.getTenantId(), rawSecret, k.getScopes());
                    } catch (Exception e) {
                        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "KEY_DECRYPTION_FAILED", "Failed to decrypt API key");
                    }
                });
    }

    public record CreatedApiKey(
            String id,
            String apiKey,
            String apiSecret,
            String name,
            String status,
            String scopes,
            Instant createdAt
    ) {
    }

    public record ResolvedApiKey(
            String keyId,
            String tenantId,
            String secret,
            String scopes
    ) {
    }

    public static class EncryptedResult {
        private final String cipherText;
        private final String iv;

        public EncryptedResult(String cipherText, String iv) {
            this.cipherText = cipherText;
            this.iv = iv;
        }

        public String getCipherText() {
            return cipherText;
        }

        public String getIv() {
            return iv;
        }
    }
}
