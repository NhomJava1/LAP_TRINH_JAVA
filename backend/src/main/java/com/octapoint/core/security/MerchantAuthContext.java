package com.octapoint.core.security;

import java.util.Arrays;

public record MerchantAuthContext(String tenantId, String keyId, String scopes) {
    public static final String REQUEST_ATTR = "octapoint.merchantAuthContext";

    public boolean hasScope(String requiredScope) {
        if (scopes == null || scopes.isBlank() || requiredScope == null || requiredScope.isBlank()) {
            return false;
        }
        String target = requiredScope.trim().toUpperCase();
        return Arrays.stream(scopes.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .anyMatch(target::equals);
    }
}
