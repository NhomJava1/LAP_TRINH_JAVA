package com.example.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter implements Filter {

    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    private Bucket resolveBucket(String key, long capacity, long refillTokens, Duration refillDuration) {
        return cache.computeIfAbsent(key, k -> {
            Bandwidth limit = Bandwidth.classic(capacity, Refill.greedy(refillTokens, refillDuration));
            return Bucket.builder().addLimit(limit).build();
        });
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

            String apiKey = httpRequest.getHeader("X-API-Key");
        String clientKey;
        long capacity;

        if (apiKey != null && !apiKey.isEmpty()) {
            clientKey = "API_KEY_" + apiKey;
            capacity = 50;
        } else {
            clientKey = "IP_" + httpRequest.getRemoteAddr();
            capacity = 10;
        }

        Bucket bucket = resolveBucket(clientKey, capacity, capacity, Duration.ofMinutes(1));

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\": \"RATE_LIMITED\", \"message\": \"Vượt quá giới hạn số lượng request. Vui lòng thử lại sau.\"}");
        }
    }
}