package com.octapoint.core.shared;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Shared HMAC-SHA256 canonical signing and verification utilities aligned with ADR-0003.
 */
public final class HmacCodec {

    private static final HexFormat HEX = HexFormat.of();

    private HmacCodec() {
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] input = bytes != null ? bytes : new byte[0];
            return HEX.formatHex(digest.digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    public static String sha256Hex(String text) {
        return sha256Hex(text != null ? text.getBytes(StandardCharsets.UTF_8) : new byte[0]);
    }

    /**
     * Canonicalizes a raw query string by RFC-3986 percent-encoding decoded key/value pairs
     * and sorting ascending by encoded key, then encoded value.
     */
    public static String canonicalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        String[] parts = rawQuery.split("&");
        List<String[]> pairs = new ArrayList<>();
        for (String part : parts) {
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
        pairs.sort(Comparator.<String[], String>comparing(p -> p[0]).thenComparing(p -> p[1]));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pairs.size(); i++) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append(pairs.get(i)[0]).append('=').append(pairs.get(i)[1]);
        }
        return sb.toString();
    }

    private static String rfc3986Encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    public static String buildStringToSign(
            String method,
            String path,
            String rawQuery,
            String timestamp,
            String nonce,
            byte[] bodyBytes) {
        String normalizedMethod = method != null ? method.toUpperCase() : "";
        String normalizedPath = path != null ? path : "";
        String canonicalQuery = canonicalizeQuery(rawQuery);
        String ts = timestamp != null ? timestamp : "";
        String nc = nonce != null ? nonce : "";
        String bodyHash = sha256Hex(bodyBytes);
        return normalizedMethod + "\n"
                + normalizedPath + "\n"
                + canonicalQuery + "\n"
                + ts + "\n"
                + nc + "\n"
                + bodyHash;
    }

    public static String hmacSha256Hex(String secret, String stringToSign) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(rawHmac);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256", e);
        }
    }

    public static boolean constantTimeEquals(String expectedHex, String providedHex) {
        if (expectedHex == null || providedHex == null) {
            return false;
        }
        byte[] expected = expectedHex.toLowerCase().getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedHex.toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }
}
