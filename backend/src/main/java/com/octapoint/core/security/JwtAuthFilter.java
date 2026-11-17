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
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        boolean isAdminRoute = path.startsWith("/api/admin/") || path.startsWith("/api/v1/admin/");
        boolean isPortalRoute = (path.startsWith("/api/portal/") && !path.startsWith("/api/portal/auth/"))
                || path.startsWith("/api/v1/merchants/api-keys");

        if (!isAdminRoute && !isPortalRoute) {
            filterChain.doFilter(request, response);
            return;
        }

        String correlationId = ensureCorrelationId(request, response);
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(response, HttpStatus.UNAUTHORIZED.value(), "AUTH_MISSING_TOKEN", "Missing Bearer token", correlationId);
            return;
        }

        PortalPrincipal principal;
        try {
            principal = jwtService.verifyToken(authHeader.substring(7).trim());
        } catch (ApiException ex) {
            sendError(response, ex.getStatus(), ex.getErrorCode(), ex.getMessage(), correlationId);
            return;
        }

        if (isAdminRoute && !"ADMIN".equals(principal.role())) {
            sendError(response, HttpStatus.FORBIDDEN.value(), "FORBIDDEN_ADMIN_ONLY", "Admin role required", correlationId);
            return;
        }
        if (isPortalRoute && !"MERCHANT".equals(principal.role())) {
            sendError(response, HttpStatus.FORBIDDEN.value(), "FORBIDDEN_MERCHANT_ONLY", "Merchant role required", correlationId);
            return;
        }

        request.setAttribute(PortalPrincipal.REQUEST_ATTR, principal);
        filterChain.doFilter(request, response);
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
                code, message.replace("\"", "\\\""), status, correlationId, Instant.now().toString()
        ));
    }
}
