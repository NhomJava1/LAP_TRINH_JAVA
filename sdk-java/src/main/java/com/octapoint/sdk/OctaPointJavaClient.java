package com.octapoint.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Official Java 21 SDK for the OctaPoint Merchant HMAC API (FR-25).
 */
public class OctaPointJavaClient {

    private final String baseUrl;
    private final String apiKey;
    private final String apiSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OctaPointJavaClient(String baseUrl, String apiKey, String apiSecret) {
        this(baseUrl, apiKey, apiSecret,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                new ObjectMapper());
    }

    public OctaPointJavaClient(
            String baseUrl,
            String apiKey,
            String apiSecret,
            HttpClient httpClient,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> issue(String customerId, String orderId, long amountVnd, String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customerId);
        if (orderId != null) {
            body.put("orderId", orderId);
        }
        body.put("amountVnd", amountVnd);
        String idem = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey
                : UUID.randomUUID().toString();
        return executeMap("POST", "/api/merchant/v1/credits/issue", null, body, Map.of("Idempotency-Key", idem));
    }

    public Map<String, Object> redeem(String customerId, String orderId, long pointsToRedeem, String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customerId);
        if (orderId != null) {
            body.put("orderId", orderId);
        }
        body.put("pointsToRedeem", pointsToRedeem);
        String idem = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey
                : UUID.randomUUID().toString();
        return executeMap("POST", "/api/merchant/v1/credits/redeem", null, body, Map.of("Idempotency-Key", idem));
    }

    public Map<String, Object> balance(String customerId) {
        String query = "customerId=" + URLEncoder.encode(customerId, StandardCharsets.UTF_8);
        return executeMap("GET", "/api/merchant/v1/credits/balance", query, null, Map.of());
    }

    public List<Map<String, Object>> history(String customerId) {
        String query = (customerId != null && !customerId.isBlank())
                ? "customerId=" + URLEncoder.encode(customerId, StandardCharsets.UTF_8)
                : null;
        String raw = executeRaw("GET", "/api/merchant/v1/credits/history", query, null, Map.of());
        try {
            return objectMapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse history response", ex);
        }
    }

    public Map<String, Object> rules() {
        return executeMap("GET", "/api/merchant/v1/credits/rules", null, null, Map.of());
    }

    public Map<String, Object> summary() {
        return executeMap("GET", "/api/merchant/v1/credits/summary", null, null, Map.of());
    }

    private Map<String, Object> executeMap(
            String method,
            String path,
            String query,
            Object payload,
            Map<String, String> extraHeaders) {
        String raw = executeRaw(method, path, query, payload, extraHeaders);
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse JSON response: " + raw, ex);
        }
    }

    private String executeRaw(
            String method,
            String path,
            String query,
            Object payload,
            Map<String, String> extraHeaders) {
        byte[] bodyBytes = new byte[0];
        if (payload != null) {
            try {
                bodyBytes = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to serialize request body", ex);
            }
        }

        Map<String, String> signedHeaders = HmacSigner.signRequest(
                apiKey, apiSecret, method, path, query, bodyBytes, null, null);
        String url = baseUrl + path + (query != null && !query.isBlank() ? "?" + query : "");

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15));

        if ("POST".equalsIgnoreCase(method)) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
        } else {
            builder.GET();
        }
        signedHeaders.forEach(builder::header);
        extraHeaders.forEach(builder::header);

        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("HTTP request failed: " + ex.getMessage(), ex);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errCode = "API_ERROR";
            String msg = "HTTP " + response.statusCode();
            try {
                Map<String, Object> errBody = objectMapper.readValue(
                        response.body(), new TypeReference<Map<String, Object>>() {
                        });
                if (errBody.get("error") != null) {
                    errCode = String.valueOf(errBody.get("error"));
                }
                if (errBody.get("message") != null) {
                    msg = String.valueOf(errBody.get("message"));
                }
            } catch (Exception ignored) {
            }
            throw new OctaPointApiException(response.statusCode(), errCode, msg);
        }
        return response.body();
    }
}
