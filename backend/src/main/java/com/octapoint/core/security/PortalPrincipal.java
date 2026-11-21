package com.octapoint.core.security;

public record PortalPrincipal(String userId, String email, String role, String tenantId) {
    public static final String REQUEST_ATTR = "octapoint.portalPrincipal";
}
