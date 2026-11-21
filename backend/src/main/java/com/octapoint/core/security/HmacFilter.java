package com.octapoint.core.security;

import com.octapoint.core.apikey.ApiKeyService;
import com.octapoint.core.audit.AuditService;
import com.octapoint.core.metrics.OctaPointMetrics;
import com.octapoint.core.shared.ApiException;
import com.octapoint.core.shared.HmacCodec;
import com.octapoint.core.tenant.Tenant;
import com.octapoint.core.tenant.TenantService;
import com.octapoint.core.tenant.TenantStatus;
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
import java.util.Optional;
import java.util.UUID;

@Component
@Order(3)
public class HmacFilter extends OncePerRequestFilter {

    private static final long ALLOWED_TIME_DIFF_SECONDS = 300;
    private static final long NONCE_TTL_SECONDS = 600;

    private final NonceStore nonceStore;
    private final ApiKeyService apiKeyService;
    private final TenantService tenantService;
    private final AuditService auditService;
    private final OctaPointMetrics metrics;

    public HmacFilter(
            NonceStore nonceStore,
            ApiKeyService apiKeyService,
            TenantService tenantService,
            AuditService auditService,
            OctaPointMetrics metrics) {
        this.nonceStore = nonceStore;
        this.apiKeyService = apiKeyService;
        this.tenantService = tenantService;
        this.auditService = auditService;
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
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
        String lowerUri = rawUri != null ? rawUri.toLowerCase() : "";
        if (lowerUri.contains("..") || lowerUri.contains(";") || lowerUri.contains("%2e") || lowerUri.contains("%3b")) {
            sendError(response, HttpStatus.BAD_REQUEST.value(), "INVALID_PATH", "Invalid request path", correlationId);
            return;
        }

        String path = rawUri != null ? rawUri.replaceAll("/+", "/") : "";
        if (!path.startsWith("/api/merchant/v1/")) {
            filterChain.doFilter(request, response);
            return;
        }
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);

        String keyId = wrappedRequest.getHeader("X-OP-Key");
        String timestampStr = wrappedRequest.getHeader("X-OP-Timestamp");
        String nonce = wrappedRequest.getHeader("X-OP-Nonce");
        String clientSignature = wrappedRequest.getHeader("X-OP-Signature");

        if (isBlank(keyId) || isBlank(timestampStr) || isBlank(nonce) || isBlank(clientSignature)) {
            sendError(response, HttpStatus.UNAUTHORIZED.value(), "AUTH_MISSING_HEADERS",
                    "Missing required X-OP-* security headers", correlationId);
            return;
        }

        long requestEpoch;
        try {
            requestEpoch = Long.parseLong(timestampStr.trim());
        } catch (NumberFormatException e) {
            sendError(response, HttpStatus.UNAUTHORIZED.value(), "AUTH_INVALID_TIMESTAMP",
                    "Invalid X-OP-Timestamp format", correlationId);
            return;
        }

        long currentEpoch = Instant.now().getEpochSecond();
        if (Math.abs(currentEpoch - requestEpoch) > ALLOWED_TIME_DIFF_SECONDS) {
            sendError(response, HttpStatus.UNAUTHORIZED.value(), "AUTH_TIMESTAMP_EXPIRED",
                    "Timestamp outside allowed +/- 300s window", correlationId);
            return;
        }

        Optional<ApiKeyService.ResolvedApiKey> resolvedOpt = apiKeyService.resolveActiveKey(keyId.trim());
        if (resolvedOpt.isEmpty()) {
            auditService.log(null, keyId.trim(), "API_KEY", "HMAC_AUTH_REJECTED", "API_KEY", keyId.trim(),
                    "REJECTED", "Unknown or revoked API key", correlationId);
            sendError(response, HttpStatus.UNAUTHORIZED.value(), "AUTH_INVALID_KEY", "Invalid or revoked API key",
                    correlationId);
            return;
        }

        ApiKeyService.ResolvedApiKey resolved = resolvedOpt.get();
        Tenant tenant = tenantService.getTenant(resolved.tenantId());
        if (tenant.getStatusEnum() != TenantStatus.APPROVED) {
            auditService.log(tenant.getId(), resolved.keyId(), "API_KEY", "HMAC_TENANT_BLOCKED", "TENANT",
                    tenant.getId(),
                    "REJECTED", "Tenant status is " + tenant.getStatus(), correlationId);
            sendError(response, HttpStatus.FORBIDDEN.value(), "TENANT_NOT_APPROVED",
                    "Tenant is not APPROVED (status=" + tenant.getStatus() + ")", correlationId);
            return;
        }

        String stringToSign = HmacCodec.buildStringToSign(
                wrappedRequest.getMethod(),
                path,
                wrappedRequest.getQueryString(),
                timestampStr.trim(),
                nonce.trim(),
                wrappedRequest.getCachedBody());
        String expectedSignature = HmacCodec.hmacSha256Hex(resolved.secret(), stringToSign);

        if (!HmacCodec.constantTimeEquals(expectedSignature, clientSignature.trim())) {
            auditService.log(tenant.getId(), resolved.keyId(), "API_KEY", "HMAC_INVALID_SIGNATURE", "API_KEY",
                    resolved.keyId(),
                    "REJECTED", "HMAC signature mismatch on " + path, correlationId);
            sendError(response, HttpStatus.UNAUTHORIZED.value(), "AUTH_INVALID_SIGNATURE", "Invalid HMAC signature",
                    correlationId);
            return;
        }

        try {
            boolean freshNonce = nonceStore.claimNonce(resolved.keyId(), nonce.trim(), NONCE_TTL_SECONDS);
            if (!freshNonce) {
                auditService.log(tenant.getId(), resolved.keyId(), "API_KEY", "HMAC_REPLAY_DETECTED", "NONCE",
                        nonce.trim(),
                        "REJECTED", "Replay attack blocked: nonce already used", correlationId);
                sendError(response, HttpStatus.UNAUTHORIZED.value(), "AUTH_NONCE_REUSED", "Nonce already used",
                        correlationId);
                return;
            }
        } catch (ApiException ex) {
            sendError(response, ex.getStatus(), ex.getErrorCode(), ex.getMessage(), correlationId);
            return;
        }

        wrappedRequest.setAttribute(
                MerchantAuthContext.REQUEST_ATTR,
                new MerchantAuthContext(tenant.getId(), resolved.keyId(), resolved.scopes()));
        filterChain.doFilter(wrappedRequest, response);
    }

    private boolean isBlank(String val) {
        return val == null || val.isBlank();
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
        metrics.recordHmacRejected();
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"error\":\"%s\",\"message\":\"%s\",\"status\":%d,\"correlationId\":\"%s\",\"timestamp\":\"%s\"}",
                code, message.replace("\"", "\\\""), status, correlationId, Instant.now().toString()));
    }
}
