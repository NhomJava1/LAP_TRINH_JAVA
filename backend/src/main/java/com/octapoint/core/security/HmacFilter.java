package com.octapoint.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Component
public class HmacFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private static final long ALLOWED_TIME_DIFF_SECONDS = 300; // lech toi da 300s
    private static final long NONCE_TTL_SECONDS = 600; // Redis TTL chong lap nonce trong 10p

    public HmacFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Chi ap dung filter cho cac duong dan merchant
        String path = request.getRequestURI();
        if (!path.startsWith("/api/merchant/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Lay cac header bao mat can thiet tu request gui len
        String apiKey = request.getHeader("X-API-Key");
        String timestampStr = request.getHeader("X-Timestamp");
        String nonce = request.getHeader("X-Nonce");
        String clientSignature = request.getHeader("X-Signature");

        if (apiKey == null || timestampStr == null || nonce == null || clientSignature == null) {
            sendError(response, HttpStatus.UNAUTHORIZED.value(), "AUTH_MISSING_HEADERS", "Missing security headers");
            return;
        }

        // 1. Kiem tra Timestamp (khong qua lech 300s)
        try {
            long requestTime = Long.parseLong(timestampStr);
            long currentTime = Instant.now().getEpochSecond();
            if (Math.abs(currentTime - requestTime) > ALLOWED_TIME_DIFF_SECONDS) {
                sendError(response, HttpStatus.UNAUTHORIZED.value(), "AUTH_TIMESTAMP_EXPIRED", "Timestamp expired");
                return;
            }
        } catch (NumberFormatException e) {
            sendError(response, HttpStatus.UNAUTHORIZED.value(), "AUTH_INVALID_TIMESTAMP", "Invalid timestamp format");
            return;
        }

        // 2. Kiem tra Nonce voi Redis (Chong Replay Attack bang SETNX)
        String nonceKey = "nonce:" + nonce;
        Boolean isNewNonce = redisTemplate.opsForValue().setIfAbsent(nonceKey, "1", NONCE_TTL_SECONDS, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(isNewNonce)) {
            sendError(response, HttpStatus.UNAUTHORIZED.value(), "AUTH_NONCE_REUSED", "Nonce already used");
            return;
        }

        // 3. Kiem tra Chu ky HMAC (Lay secret key tuong ung voi apiKey tu DB/Service)
        String secretKey = "dummy_secret_key_for_testing"; 

        try {
            String dataToSign = timestampStr + "." + nonce + "." + path;
            String computedSignature = computeHmacSha256(dataToSign, secretKey);

            // So sanh constant-time de tranh timing attack
            if (!MessageDigest.isEqual(computedSignature.getBytes(StandardCharsets.UTF_8), clientSignature.getBytes(StandardCharsets.UTF_8))) {
                sendError(response, HttpStatus.UNAUTHORIZED.value(), "AUTH_INVALID_SIGNATURE", "Invalid signature");
                return;
            }
        } catch (Exception e) {
            sendError(response, HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR", "Error processing signature");
            return;
        }

        // Neu hop le het, cho phep request di tiep
        filterChain.doFilter(request, response);
    }

    private String computeHmacSha256(String data, String secret) throws Exception {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secretKeySpec);
        byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    private void sendError(HttpServletResponse response, int status, String errorCode, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(String.format("{\"error\": \"%s\", \"message\": \"%s\"}", errorCode, message));
    }
}