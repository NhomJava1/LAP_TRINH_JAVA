package com.octapoint.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.octapoint.core.outbox.OutboxEvent;
import com.octapoint.core.outbox.OutboxEventRepository;
import com.octapoint.core.outbox.OutboxSettlementWorker;
import com.octapoint.core.shared.HmacCodec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MvpFlowAndConcurrencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxSettlementWorker outboxWorker;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void endToEndMvpFlowRbacReplayIdempotencyAndConcurrency() throws Exception {
        // 1. Register Merchant A (PENDING)
        String regBodyA = objectMapper.writeValueAsString(Map.of(
                "businessName", "Sieuthi A",
                "name", "Merchant A",
                "email", "merchanta@test.io",
                "phone", "0901000001",
                "password", "Secret123!"
        ));
        MvcResult regResA = mockMvc.perform(post("/api/portal/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBodyA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String tenantAId = objectMapper.readTree(regResA.getResponse().getContentAsString()).get("tenantId").asText();

        // Login Merchant A while PENDING -> cannot create API key yet
        String merchantAToken = login("merchanta@test.io", "Secret123!");
        mockMvc.perform(post("/api/portal/api-keys")
                        .header("Authorization", "Bearer " + merchantAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Key 1\"}"))
                .andExpect(status().isForbidden());

        // RBAC check: Merchant cannot call Admin endpoint
        mockMvc.perform(get("/api/admin/tenants")
                        .header("Authorization", "Bearer " + merchantAToken))
                .andExpect(status().isForbidden());

        // 2. Admin login & approve Merchant A
        String adminToken = login("admin@octapoint.io", "admin123");
        mockMvc.perform(get("/api/portal/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/tenants/" + tenantAId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // 3. Create API key, rotate it, and verify revoked key fails
        MvcResult keyRes1 = mockMvc.perform(post("/api/portal/api-keys")
                        .header("Authorization", "Bearer " + merchantAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Primary Key\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode key1Json = objectMapper.readTree(keyRes1.getResponse().getContentAsString());
        String key1Id = key1Json.get("apiKey").asText();
        String key1Secret = key1Json.get("apiSecret").asText();

        MvcResult keyRes2 = mockMvc.perform(post("/api/portal/api-keys/" + key1Id + "/rotate")
                        .header("Authorization", "Bearer " + merchantAToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode key2Json = objectMapper.readTree(keyRes2.getResponse().getContentAsString());
        String apiKey = key2Json.get("apiKey").asText();
        String apiSecret = key2Json.get("apiSecret").asText();
        assertNotEquals(key1Id, apiKey);

        // Old rotated key must be rejected with 401
        performSignedGet("/api/merchant/v1/credits/balance", "customerId=C1", key1Id, key1Secret)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTH_INVALID_KEY"));

        // Set rule: 1000 VND = 1 pt
        mockMvc.perform(put("/api/portal/rules")
                        .header("Authorization", "Bearer " + merchantAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"earnVndPerPoint\":1000,\"redeemValueVndPerPoint\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.earnVndPerPoint").value(1000));

        // 4. Issue 100,000 VND -> 100 pts
        String issuePayload = "{\"customerId\":\"C1\",\"orderId\":\"ORD-1\",\"amountVnd\":100000}";
        String idemKey1 = "idem-issue-1";
        String nonce1 = UUID.randomUUID().toString();
        String ts1 = String.valueOf(Instant.now().getEpochSecond());
        String sig1 = sign("POST", "/api/merchant/v1/credits/issue", null, ts1, nonce1, issuePayload, apiSecret);

        MvcResult issueRes = mockMvc.perform(post("/api/merchant/v1/credits/issue")
                        .header("Idempotency-Key", idemKey1)
                        .header("X-OP-Key", apiKey)
                        .header("X-OP-Timestamp", ts1)
                        .header("X-OP-Nonce", nonce1)
                        .header("X-OP-Signature", sig1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issuePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsIssued").value(100))
                .andExpect(jsonPath("$.balance").value(100))
                .andExpect(jsonPath("$.settlementStatus").value("PENDING"))
                .andReturn();
        String txId1 = objectMapper.readTree(issueRes.getResponse().getContentAsString()).get("transactionId").asText();

        // 5a. Idempotency retry with fresh nonce/sig returns same transactionId and doesn't double-credit
        String nonce2 = UUID.randomUUID().toString();
        String sig2 = sign("POST", "/api/merchant/v1/credits/issue", null, ts1, nonce2, issuePayload, apiSecret);
        mockMvc.perform(post("/api/merchant/v1/credits/issue")
                        .header("Idempotency-Key", idemKey1)
                        .header("X-OP-Key", apiKey)
                        .header("X-OP-Timestamp", ts1)
                        .header("X-OP-Nonce", nonce2)
                        .header("X-OP-Signature", sig2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issuePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(txId1))
                .andExpect(jsonPath("$.balance").value(100));

        // 5a conflict: same idempotency key with different payload -> 409
        String differentPayload = "{\"customerId\":\"C1\",\"orderId\":\"ORD-1\",\"amountVnd\":200000}";
        String nonce3 = UUID.randomUUID().toString();
        String sig3 = sign("POST", "/api/merchant/v1/credits/issue", null, ts1, nonce3, differentPayload, apiSecret);
        mockMvc.perform(post("/api/merchant/v1/credits/issue")
                        .header("Idempotency-Key", idemKey1)
                        .header("X-OP-Key", apiKey)
                        .header("X-OP-Timestamp", ts1)
                        .header("X-OP-Nonce", nonce3)
                        .header("X-OP-Signature", sig3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(differentPayload))
                .andExpect(status().isConflict());

        // 5b. Replay exact same signed request (nonce1 already used) -> 401 AUTH_NONCE_REUSED
        mockMvc.perform(post("/api/merchant/v1/credits/issue")
                        .header("Idempotency-Key", idemKey1)
                        .header("X-OP-Key", apiKey)
                        .header("X-OP-Timestamp", ts1)
                        .header("X-OP-Nonce", nonce1)
                        .header("X-OP-Signature", sig1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issuePayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTH_NONCE_REUSED"));

        // 5c. Wrong signature does NOT consume the fresh nonce!
        String freshNonce = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/merchant/v1/credits/issue")
                        .header("Idempotency-Key", "idem-bad-sig")
                        .header("X-OP-Key", apiKey)
                        .header("X-OP-Timestamp", ts1)
                        .header("X-OP-Nonce", freshNonce)
                        .header("X-OP-Signature", "0".repeat(64))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issuePayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTH_INVALID_SIGNATURE"));

        // Same freshNonce still usable when properly signed
        String validBalanceSig = sign("GET", "/api/merchant/v1/credits/balance", "customerId=C1", ts1, freshNonce, "", apiSecret);
        mockMvc.perform(get("/api/merchant/v1/credits/balance?customerId=C1")
                        .header("X-OP-Key", apiKey)
                        .header("X-OP-Timestamp", ts1)
                        .header("X-OP-Nonce", freshNonce)
                        .header("X-OP-Signature", validBalanceSig))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100));

        // 6. Redeem 30 pts -> 70; then redeem 100 pts -> 422 INSUFFICIENT_BALANCE
        performSignedPost("/api/merchant/v1/credits/redeem", "{\"customerId\":\"C1\",\"orderId\":\"RED-1\",\"pointsToRedeem\":30}",
                "idem-red-1", apiKey, apiSecret)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingBalance").value(70));

        performSignedPost("/api/merchant/v1/credits/redeem", "{\"customerId\":\"C1\",\"orderId\":\"RED-2\",\"pointsToRedeem\":100}",
                "idem-red-2", apiKey, apiSecret)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_BALANCE"));

        // 7. Tenant B isolation check
        String regBodyB = objectMapper.writeValueAsString(Map.of(
                "businessName", "Sieuthi B",
                "name", "Merchant B",
                "email", "merchantb@test.io",
                "phone", "0901000002",
                "password", "Secret123!"
        ));
        mockMvc.perform(post("/api/portal/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBodyB))
                .andExpect(status().isCreated());
        String merchantBToken = login("merchantb@test.io", "Secret123!");
        MvcResult bTx = mockMvc.perform(get("/api/portal/transactions")
                        .header("Authorization", "Bearer " + merchantBToken))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(0, objectMapper.readTree(bTx.getResponse().getContentAsString()).size());

        // 8. NFR-03 Concurrency Test: 100 initial pts, 20 concurrent redeems of 10 pts -> exact 10 success, 10 rejected (422), final balance = 0
        performSignedPost("/api/merchant/v1/credits/issue", "{\"customerId\":\"C_CONC\",\"orderId\":\"ORD-CONC\",\"amountVnd\":100000}",
                "idem-conc-init", apiKey, apiSecret)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100));

        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch ready = new CountDownLatch(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final int idx = i;
            tasks.add(() -> {
                ready.countDown();
                start.await();
                MvcResult res = performSignedPost(
                        "/api/merchant/v1/credits/redeem",
                        "{\"customerId\":\"C_CONC\",\"orderId\":\"CONC-" + idx + "\",\"pointsToRedeem\":10}",
                        "idem-conc-" + idx,
                        apiKey,
                        apiSecret
                ).andReturn();
                return res.getResponse().getStatus();
            });
        }
        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> t : tasks) {
            futures.add(pool.submit(t));
        }
        ready.await();
        start.countDown();

        int okCount = 0;
        int rejectedCount = 0;
        for (Future<Integer> f : futures) {
            int code = f.get();
            if (code == 200) okCount++;
            else if (code == 422) rejectedCount++;
        }
        pool.shutdown();

        assertEquals(10, okCount, "Exactly 10 concurrent redeems should succeed");
        assertEquals(10, rejectedCount, "Exactly 10 concurrent redeems should fail with 422");

        performSignedGet("/api/merchant/v1/credits/balance", "customerId=C_CONC", apiKey, apiSecret)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0));

        // 9. Outbox settlement worker + manual retry (FR-33) + Audit logs
        int settled = outboxWorker.processPendingBatch();
        assertTrue(settled >= 13);

        List<OutboxEvent> allEvents = outboxEventRepository.findAll();
        OutboxEvent firstEvent = allEvents.get(0);
        assertEquals("SETTLED", firstEvent.getStatus());
        assertEquals("MOCK", firstEvent.getSettlementMode());
        assertNull(firstEvent.getTxDigest(), "MOCK settlement must not store a fake digest");

        // Simulate FAILED event and verify Admin retry resets it to PENDING
        firstEvent.setStatus("FAILED");
        firstEvent.setLastError("Simulated RPC timeout");
        outboxEventRepository.save(firstEvent);

        mockMvc.perform(post("/api/admin/settlements/" + firstEvent.getId() + "/retry")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        // Verify Audit Logs contain entries and never leak the raw API secret
        MvcResult auditRes = mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String auditBody = auditRes.getResponse().getContentAsString();
        assertTrue(auditBody.contains("HMAC_REPLAY_DETECTED"));
        assertTrue(auditBody.contains("SETTLEMENT_MANUAL_RETRY"));
        assertTrue(!auditBody.contains(apiSecret), "Audit logs must never leak raw API secret");
    }

    private String login(String email, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private org.springframework.test.web.servlet.ResultActions performSignedPost(
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

    private org.springframework.test.web.servlet.ResultActions performSignedGet(
            String path, String query, String apiKey, String apiSecret) throws Exception {
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String sig = sign("GET", path, query, ts, nonce, "", apiSecret);
        return mockMvc.perform(get(path + "?" + query)
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
