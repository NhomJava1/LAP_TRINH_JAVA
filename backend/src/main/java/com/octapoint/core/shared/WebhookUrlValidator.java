package com.octapoint.core.shared;

import org.springframework.http.HttpStatus;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Validates merchant webhook URLs to prevent SSRF attacks against internal services and metadata endpoints.
 */
public final class WebhookUrlValidator {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "metadata.google.internal",
            "metadata",
            "169.254.169.254",
            "signer",
            "postgres",
            "redis",
            "sui",
            "core",
            "octapoint_signer"
    );

    private WebhookUrlValidator() {
    }

    public static void validate(String rawUrl, boolean allowLocalTargets) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_WEBHOOK_URL", "Webhook URL is required");
        }

        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_WEBHOOK_URL", "Malformed webhook URL");
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_WEBHOOK_URL",
                    "Webhook URL must use http or https scheme");
        }

        if (uri.getUserInfo() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_WEBHOOK_URL",
                    "Webhook URL must not contain user info credentials");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_WEBHOOK_URL",
                    "Webhook URL must specify a valid host");
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.startsWith("[") && normalizedHost.endsWith("]")) {
            normalizedHost = normalizedHost.substring(1, normalizedHost.length() - 1);
        }

        if (BLOCKED_HOSTS.contains(normalizedHost)) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_WEBHOOK_URL",
                    "Webhook host is not permitted");
        }

        boolean isLocalhost = "localhost".equals(normalizedHost) || normalizedHost.endsWith(".localhost");
        if (isLocalhost && !allowLocalTargets) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_WEBHOOK_URL",
                    "Localhost webhook targets are not permitted");
        }

        if (!normalizedHost.contains(".") && !normalizedHost.contains(":") && !(allowLocalTargets && isLocalhost)) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_WEBHOOK_URL",
                    "Single-label internal hostnames are not permitted");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(normalizedHost);
        } catch (UnknownHostException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_WEBHOOK_URL",
                    "Unable to resolve webhook host");
        }

        if (addresses.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_WEBHOOK_URL",
                    "Unable to resolve webhook host");
        }

        for (InetAddress addr : addresses) {
            if (isDisallowedAddress(addr, allowLocalTargets)) {
                throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_WEBHOOK_URL",
                        "Webhook target resolves to a disallowed internal or reserved IP address");
            }
        }
    }

    private static boolean isDisallowedAddress(InetAddress addr, boolean allowLocalTargets) {
        if (addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()
                || addr.isAnyLocalAddress()
                || addr.isMulticastAddress()) {
            return true;
        }
        if (!allowLocalTargets && addr.isLoopbackAddress()) {
            return true;
        }

        byte[] bytes = addr.getAddress();
        if (bytes.length == 4) {
            // Block CGNAT 100.64.0.0/10
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            if (first == 100 && (second & 0xc0) == 64) {
                return true;
            }
        } else if (bytes.length == 16) {
            // Block IPv6 Unique Local fc00::/7
            if ((bytes[0] & 0xfe) == 0xfc) {
                return true;
            }
        }
        return false;
    }
}
