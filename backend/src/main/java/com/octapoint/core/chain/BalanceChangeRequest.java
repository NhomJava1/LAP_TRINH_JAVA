package com.octapoint.core.chain;

import java.math.BigInteger;
import java.util.Objects;

public record BalanceChangeRequest(String accountId, BigInteger amount) {

    public BalanceChangeRequest {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId must not be blank");
        }
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
