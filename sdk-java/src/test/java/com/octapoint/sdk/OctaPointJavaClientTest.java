package com.octapoint.sdk;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OctaPointJavaClientTest {

    @Test
    void matchesCrossLanguageHmacTestVector() {
        String body = "{\"customerId\":\"C1\",\"amountVnd\":100000}";
        Map<String, String> headers = HmacSigner.signRequest(
                "op_test_key",
                "sec_shared_test_vector_2026",
                "POST",
                "/api/merchant/v1/credits/issue",
                "z=2&a=hello%20world",
                body.getBytes(StandardCharsets.UTF_8),
                "1700000000",
                "nonce-vector-001");

        assertEquals(
                "987d1a5ef200d35e8d2d849a7bdc15b1450d0f3c4bf284561a08c8e619d40e1d",
                headers.get("X-OP-Signature"));
    }

    @Test
    void verifiesWebhookSignatures() {
        String secret = "whsec_java_test";
        String ts = "1700000000";
        String body = "{\"event\":\"settlement.settled\"}";
        String sig = HmacSigner.hmacSha256Hex(secret, ts + "." + body);

        assertTrue(HmacSigner.verifyWebhookSignature(secret, ts, body, sig, 300, 1700000050));
        assertFalse(HmacSigner.verifyWebhookSignature(secret, ts, body, sig, 300, 1700000900));
        assertFalse(HmacSigner.verifyWebhookSignature(secret, ts, body, "0".repeat(64), 300, 1700000050));
    }

    @Test
    void clientSignsAndParsesHttpResponses() throws Exception {
        AtomicReference<String> seenKey = new AtomicReference<>();
        AtomicReference<String> seenSig = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/merchant/v1/credits/balance", exchange -> {
            seenKey.set(exchange.getRequestHeaders().getFirst("X-OP-Key"));
            seenSig.set(exchange.getRequestHeaders().getFirst("X-OP-Signature"));
            byte[] resp = "{\"customerId\":\"C1\",\"balance\":150}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.createContext("/api/merchant/v1/credits/issue", exchange -> {
            byte[] resp = "{\"error\":\"INSUFFICIENT_SCOPE\",\"message\":\"API key missing required scope: ISSUE\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(403, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();
        try {
            OctaPointJavaClient client = new OctaPointJavaClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "op_java_key",
                    "sec_java_test");

            Map<String, Object> bal = client.balance("C1");
            assertEquals("op_java_key", seenKey.get());
            assertEquals(64, seenSig.get().length());
            assertEquals(150, ((Number) bal.get("balance")).intValue());

            OctaPointApiException ex = assertThrows(OctaPointApiException.class,
                    () -> client.issue("C1", "ORD1", 10000, "idem-java-1"));
            assertEquals(403, ex.getStatus());
            assertEquals("INSUFFICIENT_SCOPE", ex.getErrorCode());
        } finally {
            server.stop(0);
        }
    }
}
