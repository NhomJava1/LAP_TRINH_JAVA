package com.octapoint.core.security;

public interface NonceStore {

    /**
     * Atomically claims a nonce for {@code ttlSeconds}.
     *
     * @return {@code true} if the nonce was unused and is now recorded; {@code false} on replay.
     */
    boolean claimNonce(String keyId, String nonce, long ttlSeconds);
}
