package com.octapoint.core.security;

import com.octapoint.core.shared.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Component
@Order(2)
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        applySecurityHeaders(response);

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && rawUri.startsWith(contextPath)) {
            rawUri = rawUri.substring(contextPath.length());
        }

        String correlationId = ensureCorrelationId(request, response);
        if (hasSuspiciousPathSequences(rawUri)) {
            sendError(response, HttpStatus.BAD_REQUEST.value(), "INVALID_PATH", "Invalid request path", correlationId);
            return;
        }

        String path = rawUri.replaceAll("/+", "/");
        boolean isProtectedActuator = (path.equals("/actuator") || path.startsWith("/actuator/"))
                && !"/actuator/health".equals(path) && !"/actuator/info".equals(path);
        boolean isAdminRoute = path.equals("/api/admin") || path.startsWith("/api/admin/")
                || path.equals("/api/v1/admin") || path.startsWith("/api/v1/admin/")
                || path.equals("/api/v1/api-keys") || path.startsWith("/api/v1/api-keys/")
                || isProtectedActuator;
        boolean isPortalRoute = (path.startsWith("/api/portal/") && !path.startsWith("/api/portal/auth/"))
                || path.startsWith("/api/v1/merchants/api-keys")
                || path.startsWith("/api/v1/credits/");

        if (!isAdminRoute && !isPortalRoute) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = null;
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        } else if ("/api/portal/events/stream".equals(path)) {
            String queryToken = request.getParameter("token");
            if (queryToken != null && !queryToken.isBlank()) {
                token = queryToken.trim();
            }
        }

        if (token == null || token.isBlank()) {
            sendError(response, HttpStatus.UNAUTHORIZED.value(), "AUTH_MISSING_TOKEN", "Missing Bearer token",
                    correlationId);
            return;
        }

        PortalPrincipal principal;
        try {
            principal = jwtService.verifyToken(token);
        } catch (ApiException ex) {
            sendError(response, ex.getStatus(), ex.getErrorCode(), ex.getMessage(), correlationId);
            return;
        }

        if (isAdminRoute && !"ADMIN".equals(principal.role())) {
            sendError(response, HttpStatus.FORBIDDEN.value(), "FORBIDDEN_ADMIN_ONLY", "Admin role required",
                    correlationId);
            return;
        }
        if (isPortalRoute && !"MERCHANT".equals(principal.role()) && !"STAFF".equals(principal.role())) {
            sendError(response, HttpStatus.FORBIDDEN.value(), "FORBIDDEN_MERCHANT_ONLY",
                    "Merchant or Staff role required", correlationId);
            return;
        }

        request.setAttribute(PortalPrincipal.REQUEST_ATTR, principal);
        filterChain.doFilter(request, response);
    }

    private void applySecurityHeaders(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
    }

    private boolean hasSuspiciousPathSequences(String rawUri) {
        if (rawUri == null) {
            return false;
        }
        String lower = rawUri.toLowerCase();
        return lower.contains("..") || lower.contains(";") || lower.contains("%2e") || lower.contains("%3b");
    }

    private String ensureCorrelationId(HttpServletRequest request, HttpServletResponse response) {
        Object existing = request.getAttribute("correlationId");
        if (existing instanceof String s && !s.isBlank()) {
            return s;
        }
        String header = request.getHeader("X-Correlation-Id");
        String cid = (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
        request.setAttribute("correlationId", cid);
        response.setHeader("X-Correlation-Id", cid);
        return cid;
    }

    private void sendError(HttpServletResponse response, int status, String code, String message, String correlationId)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"error\":\"%s\",\"message\":\"%s\",\"status\":%d,\"correlationId\":\"%s\",\"timestamp\":\"%s\"}",
                code, message.replace("\"", "\\\""), status, correlationId, Instant.now().toString()));
    }
}
