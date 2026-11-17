package com.octapoint.core.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "app.security.nonce-store", havingValue = "memory")
public class InMemoryNonceStore implements NonceStore {

    private final ConcurrentHashMap<String, Long> seenNonces = new ConcurrentHashMap<>();

    @Override
    public boolean claimNonce(String keyId, String nonce, long ttlSeconds) {
        long now = Instant.now().getEpochSecond();
        String composite = keyId + ":" + nonce;
        seenNonces.entrySet().removeIf(entry -> entry.getValue() < now);
        return seenNonces.putIfAbsent(composite, now + ttlSeconds) == null;
    }
}
