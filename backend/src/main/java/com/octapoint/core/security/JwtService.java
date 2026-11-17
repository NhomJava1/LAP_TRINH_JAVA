package com.octapoint.core.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.octapoint.core.shared.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtService {

    private static final long TOKEN_TTL_SECONDS = 86_400; // 24 hours
    private static final Base64.Encoder URL_ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DEC = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secretBytes;

    public JwtService(
            ObjectMapper objectMapper,
            @Value("${app.security.jwt-secret}") String jwtSecret) {
        this.objectMapper = objectMapper;
        this.secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
    }

    public String generateToken(AppUser user) {
        try {
            String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            long now = Instant.now().getEpochSecond();
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", user.getId());
            claims.put("email", user.getEmail());
            claims.put("role", user.getRole());
            if (user.getTenantId() != null) {
                claims.put("tenantId", user.getTenantId());
            }
            claims.put("iat", now);
            claims.put("exp", now + TOKEN_TTL_SECONDS);

            String headerB64 = URL_ENC.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String payloadB64 = URL_ENC.encodeToString(objectMapper.writeValueAsBytes(claims));
            String signingInput = headerB64 + "." + payloadB64;
            String sigB64 = URL_ENC.encodeToString(hmacSha256(signingInput));
            return signingInput + "." + sigB64;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate JWT", e);
        }
    }

    public PortalPrincipal verifyToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED.value(), "AUTH_MISSING_TOKEN", "Missing Bearer token");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new ApiException(HttpStatus.UNAUTHORIZED.value(), "AUTH_INVALID_TOKEN", "Malformed JWT");
        }

        try {
            String signingInput = parts[0] + "." + parts[1];
            byte[] expectedSig = hmacSha256(signingInput);
            byte[] providedSig = URL_DEC.decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSig, providedSig)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED.value(), "AUTH_INVALID_TOKEN", "Invalid JWT signature");
            }

            byte[] payloadBytes = URL_DEC.decode(parts[1]);
            Map<String, Object> claims = objectMapper.readValue(payloadBytes, new TypeReference<>() {});
            Object expObj = claims.get("exp");
            if (!(expObj instanceof Number expNum) || expNum.longValue() < Instant.now().getEpochSecond()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED.value(), "AUTH_TOKEN_EXPIRED", "JWT expired");
            }

            String userId = String.valueOf(claims.get("sub"));
            String email = String.valueOf(claims.get("email"));
            String role = String.valueOf(claims.get("role"));
            String tenantId = claims.get("tenantId") != null ? String.valueOf(claims.get("tenantId")) : null;
            return new PortalPrincipal(userId, email, role, tenantId);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED.value(), "AUTH_INVALID_TOKEN", "Invalid JWT");
        }
    }

    private byte[] hmacSha256(String input) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
        return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
    }
}
