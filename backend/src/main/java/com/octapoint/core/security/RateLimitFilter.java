package com.octapoint.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final long maxRequestsPerMinute;
    private final long maxLoginRequestsPerMinute;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${app.security.rate-limit-capacity:120}") long maxRequestsPerMinute,
            @Value("${app.security.login-rate-limit-capacity:20}") long maxLoginRequestsPerMinute) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.maxLoginRequestsPerMinute = maxLoginRequestsPerMinute;
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
        String path = rawUri != null ? rawUri.replaceAll("/+", "/") : "";
        if ("/actuator/health".equals(path) || "/actuator/info".equals(path) || path.startsWith("/h2-console")) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean isLoginRoute = "/api/portal/auth/login".equals(path);
        String apiKey = request.getHeader("X-OP-Key");
        String bucketKey;
        long limit;
        if (isLoginRoute) {
            bucketKey = "LOGIN_IP:" + request.getRemoteAddr();
            limit = maxLoginRequestsPerMinute;
        } else if (path.startsWith("/api/merchant/v1/") && apiKey != null && !apiKey.isBlank()) {
            bucketKey = "KEY:" + apiKey.trim();
            limit = maxRequestsPerMinute;
        } else {
            bucketKey = "IP:" + request.getRemoteAddr();
            limit = maxRequestsPerMinute;
        }

        long nowMinute = Instant.now().getEpochSecond() / 60;
        WindowCounter counter = counters.compute(bucketKey, (k, existing) -> {
            if (existing == null || existing.minuteEpoch != nowMinute) {
                return new WindowCounter(nowMinute, 1);
            }
            existing.count++;
            return existing;
        });

        if (counter.count > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter()
                    .write("{\"error\":\"RATE_LIMITED\",\"message\":\"Rate limit exceeded\",\"status\":429}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static final class WindowCounter {
        final long minuteEpoch;
        long count;

        WindowCounter(long minuteEpoch, long count) {
            this.minuteEpoch = minuteEpoch;
            this.count = count;
        }
    }
}
