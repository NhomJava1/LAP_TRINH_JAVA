package com.octapoint.core.security;

public record MerchantAuthContext(String tenantId, String keyId, String scopes) {
    public static final String REQUEST_ATTR = "octapoint.merchantAuthContext";
}
