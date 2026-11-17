package com.octapoint.core.shared;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HmacCodecTest {

    @Test
    void canonicalizesQueryWithSortingAndRfc3986Encoding() {
        assertEquals("", HmacCodec.canonicalizeQuery(null));
        assertEquals("", HmacCodec.canonicalizeQuery(""));
        assertEquals("a=1&a=2&b=hello%20world&tilde=~",
                HmacCodec.canonicalizeQuery("b=hello+world&a=2&tilde=~&a=1"));
    }

    @Test
    void producesDeterministicSharedTestVector() {
        String secret = "sec_shared_test_vector_2026";
        String method = "post";
        String path = "/api/merchant/v1/credits/issue";
        String query = "z=2&a=hello%20world";
        String timestamp = "1700000000";
        String nonce = "nonce-vector-001";
        byte[] body = "{\"customerId\":\"C1\",\"amountVnd\":100000}".getBytes(StandardCharsets.UTF_8);

        String expectedStringToSign = "POST\n"
                + "/api/merchant/v1/credits/issue\n"
                + "a=hello%20world&z=2\n"
                + "1700000000\n"
                + "nonce-vector-001\n"
                + HmacCodec.sha256Hex(body);

        String actualStringToSign = HmacCodec.buildStringToSign(method, path, query, timestamp, nonce, body);
        assertEquals(expectedStringToSign, actualStringToSign);

        String sig = HmacCodec.hmacSha256Hex(secret, actualStringToSign);
        assertEquals("987d1a5ef200d35e8d2d849a7bdc15b1450d0f3c4bf284561a08c8e619d40e1d", sig);
        assertTrue(HmacCodec.constantTimeEquals(sig, sig.toUpperCase()));
        assertFalse(HmacCodec.constantTimeEquals(sig, "0".repeat(64)));
    }
}
