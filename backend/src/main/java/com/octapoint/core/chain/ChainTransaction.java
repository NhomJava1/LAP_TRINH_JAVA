package com.octapoint.core.chain;

public record ChainTransaction(String digest, long gasMist) {

    public ChainTransaction {
        if (digest == null || digest.isBlank()) {
            throw new IllegalArgumentException("digest must not be blank");
        }
        if (gasMist < 0) {
            throw new IllegalArgumentException("gasMist must not be negative");
        }
    }

    public ChainTransaction(String digest) {
        this(digest, 0L);
    }
}
