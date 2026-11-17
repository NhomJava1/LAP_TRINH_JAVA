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
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${app.security.rate-limit-capacity:120}") long maxRequestsPerMinute) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        if (path.startsWith("/actuator/") || path.startsWith("/h2-console")) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader("X-OP-Key");
        String bucketKey = (apiKey != null && !apiKey.isBlank())
                ? "KEY:" + apiKey
                : "IP:" + request.getRemoteAddr();

        long nowMinute = Instant.now().getEpochSecond() / 60;
        WindowCounter counter = counters.compute(bucketKey, (k, existing) -> {
            if (existing == null || existing.minuteEpoch != nowMinute) {
                return new WindowCounter(nowMinute, 1);
            }
            existing.count++;
            return existing;
        });

        if (counter.count > maxRequestsPerMinute) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"RATE_LIMITED\",\"message\":\"Rate limit exceeded\",\"status\":429}");
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
