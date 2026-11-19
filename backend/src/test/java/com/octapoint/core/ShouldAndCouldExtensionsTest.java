package com.octapoint.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.octapoint.core.outbox.OutboxEvent;
import com.octapoint.core.outbox.OutboxEventRepository;
import com.octapoint.core.outbox.OutboxSettlementWorker;
import com.octapoint.core.shared.HmacCodec;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:octapoint_ext_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class ShouldAndCouldExtensionsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxSettlementWorker outboxWorker;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void shouldAndCouldExtensionsEndToEnd() throws Exception {
        // 1. Register & approve dedicated extension test merchant
        String regBody = objectMapper.writeValueAsString(Map.of(
                "businessName", "OctaMart Extensions",
                "name", "Extension Owner",
                "email", "ext-merchant@test.io",
                "phone", "0909888777",
                "password", "Secret123!"
        ));
        MvcResult regRes = mockMvc.perform(post("/api/portal/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isCreated())
                .andReturn();
        String tenantId = objectMapper.readTree(regRes.getResponse().getContentAsString()).get("tenantId").asText();

        String adminToken = login("admin@octapoint.io", "admin123");
        mockMvc.perform(patch("/api/admin/tenants/" + tenantId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        String merchantToken = login("ext-merchant@test.io", "Secret123!");

        // 2. Create FULL key & READ-only key (FR-17 / ADR-0003 scope enforcement)
        MvcResult fullKeyRes = mockMvc.perform(post("/api/portal/api-keys")
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"POS Full\",\"scopes\":\"ISSUE,REDEEM,READ\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scopes").value("ISSUE,REDEEM,READ"))
                .andReturn();
        JsonNode fullKeyJson = objectMapper.readTree(fullKeyRes.getResponse().getContentAsString());
        String fullKey = fullKeyJson.get("apiKey").asText();
        String fullSecret = fullKeyJson.get("apiSecret").asText();

        MvcResult readKeyRes = mockMvc.perform(post("/api/portal/api-keys")
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"MCP Read Only\",\"scopes\":\"READ\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scopes").value("READ"))
                .andReturn();
        JsonNode readKeyJson = objectMapper.readTree(readKeyRes.getResponse().getContentAsString());
        String readKey = readKeyJson.get("apiKey").asText();
        String readSecret = readKeyJson.get("apiSecret").asText();

        performSignedPost("/api/merchant/v1/credits/issue",
                "{\"customerId\":\"EXT_C1\",\"orderId\":\"ORD_R1\",\"amountVnd\":10000}",
                "idem-read-block-issue", readKey, readSecret)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_SCOPE"));

        performSignedPost("/api/merchant/v1/credits/redeem",
                "{\"customerId\":\"EXT_C1\",\"orderId\":\"ORD_R2\",\"pointsToRedeem\":5}",
                "idem-read-block-redeem", readKey, readSecret)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_SCOPE"));

        performSignedGet("/api/merchant/v1/credits/balance", "customerId=EXT_C1", readKey, readSecret)
                .andExpect(status().isOk());
        performSignedGet("/api/merchant/v1/credits/rules", null, readKey, readSecret)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenantId));
        performSignedGet("/api/merchant/v1/credits/summary", null, readKey, readSecret)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenantId));

        // 3. Configure Rule Engine with Silver/Gold tier multipliers & expiry (FR-18)
        String rulePayload = objectMapper.writeValueAsString(Map.of(
                "earnVndPerPoint", 1000,
                "redeemValueVndPerPoint", 1000,
                "pointExpiryDays", 90,
                "silverThresholdPoints", 100,
                "goldThresholdPoints", 300,
                "silverMultiplier", 1.50,
                "goldMultiplier", 2.00
        ));
        mockMvc.perform(put("/api/portal/rules")
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rulePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointExpiryDays").value(90))
                .andExpect(jsonPath("$.silverThresholdPoints").value(100))
                .andExpect(jsonPath("$.goldThresholdPoints").value(300));

        // Issue 1: BRONZE (lifetime 0 < 100) -> 100 * 1.0 = 100 pts (new lifetime = 100)
        performSignedPost("/api/merchant/v1/credits/issue",
                "{\"customerId\":\"EXT_C1\",\"orderId\":\"ORD_TIER_1\",\"amountVnd\":100000}",
                "idem-tier-1", fullKey, fullSecret)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("BRONZE"))
                .andExpect(jsonPath("$.pointsIssued").value(100))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());

        // Issue 2: SILVER (lifetime 100 >= 100) -> 200 * 1.5 = 300 pts (new lifetime = 400)
        performSignedPost("/api/merchant/v1/credits/issue",
                "{\"customerId\":\"EXT_C1\",\"orderId\":\"ORD_TIER_2\",\"amountVnd\":200000}",
                "idem-tier-2", fullKey, fullSecret)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("SILVER"))
                .andExpect(jsonPath("$.pointsIssued").value(300));

        // Issue 3: GOLD (lifetime 400 >= 300) -> 100 * 2.0 = 200 pts
        performSignedPost("/api/merchant/v1/credits/issue",
                "{\"customerId\":\"EXT_C1\",\"orderId\":\"ORD_TIER_3\",\"amountVnd\":100000}",
                "idem-tier-3", fullKey, fullSecret)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("GOLD"))
                .andExpect(jsonPath("$.pointsIssued").value(200));

        // 4. Anomaly detection (FR-22): issue >= 5000 points for EXT_C2
        performSignedPost("/api/merchant/v1/credits/issue",
                "{\"customerId\":\"EXT_C2\",\"orderId\":\"ORD_ANOM_1\",\"amountVnd\":6000000}",
                "idem-anom-1", fullKey, fullSecret)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsIssued").value(6000));

        MvcResult tenantAnomRes = mockMvc.perform(get("/api/portal/anomalies")
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andReturn();
        String tenantAnomBody = tenantAnomRes.getResponse().getContentAsString();
        assertTrue(tenantAnomBody.contains("HIGH_SINGLE_ISSUE"));
        assertTrue(tenantAnomBody.contains("EXT_C2"));

        MvcResult adminAnomRes = mockMvc.perform(get("/api/admin/anomalies")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(adminAnomRes.getResponse().getContentAsString().contains("HIGH_SINGLE_ISSUE"));

        // 5. Analytics (FR-19) & Simulated Billing (FR-20)
        mockMvc.perform(get("/api/portal/analytics")
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").value(4))
                .andExpect(jsonPath("$.daily").isArray())
                .andExpect(jsonPath("$.topCustomers").isArray());

        mockMvc.perform(get("/api/admin/analytics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").isNumber());

        mockMvc.perform(put("/api/portal/billing/plan")
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"GROWTH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("GROWTH"));

        mockMvc.perform(get("/api/portal/billing")
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("GROWTH"))
                .andExpect(jsonPath("$.baseFeeVnd").value(1490000))
                .andExpect(jsonPath("$.billingMode").value("SIMULATED"));

        // 6. Webhook HMAC delivery & signature verification (FR-28)
        AtomicReference<String> capturedTs = new AtomicReference<>();
        AtomicReference<String> capturedSig = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();

        HttpServer hookServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        hookServer.createContext("/hook", exchange -> {
            byte[] reqBytes = exchange.getRequestBody().readAllBytes();
            capturedTs.set(exchange.getRequestHeaders().getFirst("X-OP-Webhook-Timestamp"));
            capturedSig.set(exchange.getRequestHeaders().getFirst("X-OP-Webhook-Signature"));
            capturedBody.set(new String(reqBytes, StandardCharsets.UTF_8));
            byte[] resp = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        hookServer.start();
        try {
            int hookPort = hookServer.getAddress().getPort();
            String webhookSecret = "whsec_test_123";
            mockMvc.perform(put("/api/portal/webhook")
                            .header("Authorization", "Bearer " + merchantToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "webhookUrl", "http://127.0.0.1:" + hookPort + "/hook",
                                    "webhookSecret", webhookSecret
                            ))))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/portal/webhook/test")
                            .header("Authorization", "Bearer " + merchantToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DELIVERED"));

            assertNotNull(capturedTs.get());
            assertNotNull(capturedSig.get());
            assertNotNull(capturedBody.get());
            String expectedSig = HmacCodec.hmacSha256Hex(webhookSecret, capturedTs.get() + "." + capturedBody.get());
            assertEquals(expectedSig, capturedSig.get());
        } finally {
            hookServer.stop(0);
        }

        // 7. PTB batching (FR-26), Reconciliation (FR-29), Anchoring (FR-30), Metrics (NFR-06)
        // Drain pending events in per-customer order across batches so all queued events settle cleanly.
        int totalSettled = 0;
        for (int i = 0; i < 10; i++) {
            int batch = outboxWorker.processPendingBatch();
            if (batch == 0) break;
            totalSettled += batch;
        }
        assertTrue(totalSettled >= 4);

        mockMvc.perform(post("/api/admin/reconcile")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCHED"));

        MvcResult anchorRes = mockMvc.perform(post("/api/admin/anchors")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANCHORED"))
                .andReturn();
        JsonNode anchorJson = objectMapper.readTree(anchorRes.getResponse().getContentAsString());
        assertFalse(anchorJson.get("batchSha256Hash").asText().isBlank());
        assertFalse(anchorJson.get("suiTxDigest").asText().isBlank());

        MvcResult metricsRes = mockMvc.perform(get("/api/admin/metrics-summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode metricsJson = objectMapper.readTree(metricsRes.getResponse().getContentAsString());
        assertTrue(metricsJson.get("creditsIssuedCount").asLong() >= 4);
        assertTrue(metricsJson.get("anomaliesDetectedCount").asLong() >= 1);
        assertTrue(metricsJson.get("settlementSettledCount").asLong() >= 4);

        // 8. Gas Quota Pause & Admin Resume (FR-27)
        mockMvc.perform(patch("/api/admin/tenants/" + tenantId + "/gas-quota")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gasQuotaMist\":1}"))
                .andExpect(status().isOk());

        MvcResult pausedIssueRes = performSignedPost("/api/merchant/v1/credits/issue",
                "{\"customerId\":\"EXT_C3\",\"orderId\":\"ORD_GAS_1\",\"amountVnd\":10000}",
                "idem-gas-pause-1", fullKey, fullSecret)
                .andExpect(status().isOk())
                .andReturn();
        String gasEventId = objectMapper.readTree(pausedIssueRes.getResponse().getContentAsString()).get("eventId").asText();

        outboxWorker.processPendingBatch();
        OutboxEvent pausedEvt = outboxEventRepository.findById(gasEventId).orElseThrow();
        assertEquals("PAUSED_GAS_QUOTA", pausedEvt.getStatus());

        mockMvc.perform(patch("/api/admin/tenants/" + tenantId + "/gas-quota")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gasQuotaMist\":50000000000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumedEvents").value(1));

        outboxWorker.processPendingBatch();
        OutboxEvent resumedEvt = outboxEventRepository.findById(gasEventId).orElseThrow();
        assertEquals("SETTLED", resumedEvt.getStatus());

        // 9. STAFF read-only role (FR-31) & SSE stream auth (FR-23)
        mockMvc.perform(post("/api/portal/staff")
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Staff Viewer",
                                "email", "ext-staff@test.io",
                                "password", "StaffSecret123!"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STAFF"));

        String staffToken = login("ext-staff@test.io", "StaffSecret123!");

        mockMvc.perform(get("/api/portal/dashboard")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STAFF"));

        mockMvc.perform(get("/api/portal/transactions")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/portal/analytics")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/portal/events/stream?token=" + staffToken))
                .andExpect(status().isOk());

        List<String> ownerOnlyGets = List.of("/api/portal/api-keys", "/api/portal/staff", "/api/portal/billing", "/api/portal/webhook");
        for (String path : ownerOnlyGets) {
            mockMvc.perform(get(path).header("Authorization", "Bearer " + staffToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN_MERCHANT_OWNER_ONLY"));
        }

        mockMvc.perform(put("/api/portal/rules")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rulePayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN_MERCHANT_OWNER_ONLY"));

        // Legacy v1 endpoints must require JWT auth and enforce role/tenant isolation
        mockMvc.perform(get("/api/v1/credits/balance?customerId=C_EXT&tenantId=spoofed"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTH_MISSING_TOKEN"));

        mockMvc.perform(post("/api/v1/api-keys"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTH_MISSING_TOKEN"));

        mockMvc.perform(post("/api/v1/api-keys")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN_ADMIN_ONLY"));

        mockMvc.perform(get("/api/v1/credits/balance?customerId=C_EXT&tenantId=spoofed")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("C_EXT"));
    }

    private String login(String email, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private ResultActions performSignedPost(
            String path, String payload, String idemKey, String apiKey, String apiSecret) throws Exception {
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String sig = sign("POST", path, null, ts, nonce, payload, apiSecret);
        return mockMvc.perform(post(path)
                .header("Idempotency-Key", idemKey)
                .header("X-OP-Key", apiKey)
                .header("X-OP-Timestamp", ts)
                .header("X-OP-Nonce", nonce)
                .header("X-OP-Signature", sig)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));
    }

    private ResultActions performSignedGet(
            String path, String query, String apiKey, String apiSecret) throws Exception {
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String sig = sign("GET", path, query, ts, nonce, "", apiSecret);
        String fullUrl = query == null || query.isBlank() ? path : path + "?" + query;
        return mockMvc.perform(get(fullUrl)
                .header("X-OP-Key", apiKey)
                .header("X-OP-Timestamp", ts)
                .header("X-OP-Nonce", nonce)
                .header("X-OP-Signature", sig));
    }

    private String sign(String method, String path, String query, String ts, String nonce, String body, String secret) {
        String s2s = HmacCodec.buildStringToSign(method, path, query, ts, nonce, body.getBytes(StandardCharsets.UTF_8));
        return HmacCodec.hmacSha256Hex(secret, s2s);
    }
}
