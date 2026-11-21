package com.octapoint.core.security;

import com.octapoint.core.shared.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.security.nonce-store", havingValue = "redis", matchIfMissing = true)
public class RedisNonceStore implements NonceStore {

    private final StringRedisTemplate redisTemplate;

    public RedisNonceStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean claimNonce(String keyId, String nonce, long ttlSeconds) {
        String redisKey = "nonce:" + keyId + ":" + nonce;
        try {
            Boolean claimed = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", ttlSeconds, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(claimed);
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "AUTH_REPLAY_STORE_UNAVAILABLE",
                    "Replay protection store unavailable"
            );
        }
    }
}
