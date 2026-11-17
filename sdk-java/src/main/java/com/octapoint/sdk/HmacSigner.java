package com.octapoint.sdk;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ADR-0003 HMAC-SHA256 signer and webhook verifier for the OctaPoint Java SDK.
 */
public final class HmacSigner {

    private HmacSigner() {
    }

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data != null ? data : new byte[0]);
            return toHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public static String canonicalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        String normalized = rawQuery.startsWith("?") ? rawQuery.substring(1) : rawQuery;
        List<String[]> pairs = new ArrayList<>();
        for (String part : normalized.split("&")) {
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            String rawKey = eq >= 0 ? part.substring(0, eq) : part;
            String rawVal = eq >= 0 ? part.substring(eq + 1) : "";
            String encKey = rfc3986Encode(URLDecoder.decode(rawKey, StandardCharsets.UTF_8));
            String encVal = rfc3986Encode(URLDecoder.decode(rawVal, StandardCharsets.UTF_8));
            pairs.add(new String[]{encKey, encVal});
        }
        pairs.sort(Comparator.comparing((String[] p) -> p[0]).thenComparing(p -> p[1]));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pairs.size(); i++) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append(pairs.get(i)[0]).append('=').append(pairs.get(i)[1]);
        }
        return sb.toString();
    }

    public static String buildStringToSign(
            String method,
            String path,
            String query,
            String timestamp,
            String nonce,
            byte[] body) {
        return String.join("\n",
                method.toUpperCase(),
                path,
                canonicalizeQuery(query),
                timestamp,
                nonce,
                sha256Hex(body));
    }

    public static String hmacSha256Hex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return toHex(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 calculation failed", ex);
        }
    }

    public static Map<String, String> signRequest(
            String apiKey,
            String apiSecret,
            String method,
            String path,
            String query,
            byte[] body,
            String timestamp,
            String nonce) {
        String ts = (timestamp != null && !timestamp.isBlank()) ? timestamp : String.valueOf(Instant.now().getEpochSecond());
        String n = (nonce != null && !nonce.isBlank()) ? nonce : UUID.randomUUID().toString();
        String s2s = buildStringToSign(method, path, query, ts, n, body);
        String sig = hmacSha256Hex(apiSecret, s2s);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-OP-Key", apiKey);
        headers.put("X-OP-Timestamp", ts);
        headers.put("X-OP-Nonce", n);
        headers.put("X-OP-Signature", sig);
        return headers;
    }

    public static boolean verifyWebhookSignature(
            String webhookSecret,
            String timestamp,
            String rawBody,
            String signature,
            long toleranceSeconds,
            long nowEpochSeconds) {
        if (webhookSecret == null || timestamp == null || rawBody == null || signature == null) {
            return false;
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException ex) {
            return false;
        }
        if (Math.abs(nowEpochSeconds - ts) > toleranceSeconds) {
            return false;
        }
        String expected = hmacSha256Hex(webhookSecret, timestamp + "." + rawBody);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private static String rfc3986Encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
