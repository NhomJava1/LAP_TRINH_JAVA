package com.octapoint.core.chain;

public record ChainTransaction(String digest) {

    public ChainTransaction {
        if (digest == null || digest.isBlank()) {
            throw new IllegalArgumentException("digest must not be blank");
        }
    }
}
