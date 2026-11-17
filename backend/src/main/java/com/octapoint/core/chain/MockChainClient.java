package com.octapoint.core.chain;

import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MockChainClient implements ChainClient {

    private final ConcurrentHashMap<String, BigInteger> accounts = new ConcurrentHashMap<>();

    @Override
    public ChainAccount createAccount(CreateAccountRequest request) {
        String accountId = "mock_acc_" + request.ownerId();
        BigInteger balance = accounts.computeIfAbsent(accountId, ignored -> BigInteger.ZERO);
        return new ChainAccount(accountId, balance);
    }

    @Override
    public ChainTransaction mint(BalanceChangeRequest request) {
        accounts.merge(request.accountId(), request.amount(), BigInteger::add);
        return new ChainTransaction("mock_tx_" + UUID.randomUUID().toString().replace("-", ""));
    }

    @Override
    public ChainTransaction redeem(BalanceChangeRequest request) {
        accounts.compute(request.accountId(), (key, current) -> {
            BigInteger existing = current != null ? current : BigInteger.ZERO;
            if (existing.compareTo(request.amount()) < 0) {
                throw new IllegalStateException("Insufficient on-chain mock balance for " + key);
            }
            return existing.subtract(request.amount());
        });
        return new ChainTransaction("mock_tx_" + UUID.randomUUID().toString().replace("-", ""));
    }

    @Override
    public Optional<ChainAccount> getAccount(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(accounts.get(accountId))
                .map(balance -> new ChainAccount(accountId, balance));
    }
}
