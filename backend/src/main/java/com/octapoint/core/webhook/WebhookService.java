package com.octapoint.core.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.octapoint.core.outbox.OutboxEvent;
import com.octapoint.core.shared.ApiException;
import com.octapoint.core.shared.HmacCodec;
import com.octapoint.core.shared.WebhookUrlValidator;
import com.octapoint.core.tenant.Tenant;
import com.octapoint.core.tenant.TenantService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WebhookService {

    private static final int MAX_ATTEMPTS = 3;

    private final WebhookDeliveryRepository deliveryRepository;
    private final TenantService tenantService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean allowLocalWebhookTargets;

    public WebhookService(
            WebhookDeliveryRepository deliveryRepository,
            TenantService tenantService,
            ObjectMapper objectMapper,
            @Value("${app.security.webhook.allow-local-targets:false}") boolean allowLocalWebhookTargets) {
        this.deliveryRepository = deliveryRepository;
        this.tenantService = tenantService;
        this.objectMapper = objectMapper;
        this.allowLocalWebhookTargets = allowLocalWebhookTargets;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public void notifySettled(OutboxEvent evt) {
        if (evt.getTenantId() == null) {
            return;
        }
        Tenant tenant = tenantService.getTenant(evt.getTenantId());
        if (tenant.getWebhookUrl() == null || tenant.getWebhookUrl().isBlank()) {
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", "settlement.settled");
        body.put("eventId", evt.getId());
        body.put("transactionId", evt.getAggregateId());
        body.put("tenantId", evt.getTenantId());
        body.put("customerId", evt.getCustomerId());
        body.put("eventType", evt.getEventType());
        body.put("chainSeq", evt.getChainSeq());
        body.put("settlementStatus", evt.getStatus());
        body.put("settlementMode", evt.getSettlementMode());
        body.put("txDigest", evt.getTxDigest());
        body.put("settledAt", evt.getSettledAt() != null ? evt.getSettledAt().toString() : Instant.now().toString());

        sendWithRetries(tenant, evt.getId(), body);
    }

    public WebhookDelivery sendTestWebhook(String tenantId) {
        Tenant tenant = tenantService.getTenant(tenantId);
        if (tenant.getWebhookUrl() == null || tenant.getWebhookUrl().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "WEBHOOK_NOT_CONFIGURED",
                    "Please configure a webhook URL first");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", "webhook.test");
        body.put("tenantId", tenantId);
        body.put("message", "OctaPoint HMAC webhook test notification");
        body.put("timestamp", Instant.now().toString());
        return sendWithRetries(tenant, "test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12), body);
    }

    public List<WebhookDelivery> listByTenant(String tenantId) {
        return deliveryRepository.findTop50ByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    private WebhookDelivery sendWithRetries(Tenant tenant, String eventId, Map<String, Object> payloadMap) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payloadMap);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize webhook payload", e);
        }

        String secret = (tenant.getWebhookSecret() != null && !tenant.getWebhookSecret().isBlank())
                ? tenant.getWebhookSecret()
                : "default-webhook-secret";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = HmacCodec.hmacSha256Hex(secret, timestamp + "." + payloadJson);

        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setId("whd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        delivery.setTenantId(tenant.getId());
        delivery.setEventId(eventId);
        delivery.setTargetUrl(tenant.getWebhookUrl());
        delivery.setPayload(payloadJson);
        delivery.setSignature(signature);
        delivery.setStatus("PENDING");

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            delivery.setAttemptCount(attempt);
            try {
                WebhookUrlValidator.validate(tenant.getWebhookUrl(), allowLocalWebhookTargets);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(tenant.getWebhookUrl()))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .header("X-OP-Webhook-Timestamp", timestamp)
                        .header("X-OP-Webhook-Signature", signature)
                        .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                        .build();

                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                delivery.setLastHttpStatus(res.statusCode());
                if (res.statusCode() >= 200 && res.statusCode() < 300) {
                    delivery.setStatus("DELIVERED");
                    delivery.setLastError(null);
                    delivery.setDeliveredAt(Instant.now());
                    return deliveryRepository.save(delivery);
                }
                delivery.setLastError("HTTP " + res.statusCode());
            } catch (ApiException ex) {
                delivery.setLastError("Webhook target disallowed");
                break;
            } catch (Exception ex) {
                delivery.setLastError("Webhook delivery failed");
            }
        }

        delivery.setStatus("FAILED");
        return deliveryRepository.save(delivery);
    }
}
