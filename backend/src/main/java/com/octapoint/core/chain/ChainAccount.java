package com.octapoint.core.chain;

import java.math.BigInteger;
import java.util.Objects;

public record ChainAccount(String accountId, BigInteger balance) {

    public ChainAccount {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId must not be blank");
        }
        Objects.requireNonNull(balance, "balance must not be null");
        if (balance.signum() < 0) {
            throw new IllegalArgumentException("balance must not be negative");
        }
    }
}
