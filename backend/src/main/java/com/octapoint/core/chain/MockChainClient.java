package com.octapoint.core.chain;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class MockChainClient implements ChainClient {

    private final ConcurrentHashMap<String, BigInteger> accounts = new ConcurrentHashMap<>();
    private final AtomicReference<BigInteger> totalBalance = new AtomicReference<>(BigInteger.ZERO);

    @Override
    public ChainAccount createAccount(CreateAccountRequest request) {
        String accountId = "mock_acc_" + request.ownerId();
        BigInteger balance = accounts.computeIfAbsent(accountId, ignored -> BigInteger.ZERO);
        return new ChainAccount(accountId, balance);
    }

    @Override
    public ChainTransaction mint(BalanceChangeRequest request) {
        accounts.merge(request.accountId(), request.amount(), BigInteger::add);
        totalBalance.updateAndGet(cur -> cur.add(request.amount()));
        return new ChainTransaction("mock_tx_" + UUID.randomUUID().toString().replace("-", ""), 1_000_000L);
    }

    @Override
    public ChainTransaction mintBatch(List<BigInteger> amounts) {
        if (amounts == null || amounts.isEmpty()) {
            throw new IllegalArgumentException("amounts must not be empty");
        }
        BigInteger sum = BigInteger.ZERO;
        for (BigInteger amt : amounts) {
            if (amt == null || amt.signum() <= 0) {
                throw new IllegalArgumentException("every amount must be positive");
            }
            sum = sum.add(amt);
        }
        BigInteger finalSum = sum;
        totalBalance.updateAndGet(cur -> cur.add(finalSum));
        return new ChainTransaction("mock_ptb_" + UUID.randomUUID().toString().replace("-", ""), 1_500_000L);
    }

    @Override
    public ChainTransaction mintBatchRequests(List<BalanceChangeRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("requests must not be empty");
        }
        BigInteger sum = BigInteger.ZERO;
        for (BalanceChangeRequest req : requests) {
            if (req.amount() == null || req.amount().signum() <= 0) {
                throw new IllegalArgumentException("every amount must be positive");
            }
            accounts.merge(req.accountId(), req.amount(), BigInteger::add);
            sum = sum.add(req.amount());
        }
        BigInteger finalSum = sum;
        totalBalance.updateAndGet(cur -> cur.add(finalSum));
        return new ChainTransaction("mock_ptb_" + UUID.randomUUID().toString().replace("-", ""), 1_500_000L);
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
        totalBalance.updateAndGet(cur -> cur.subtract(request.amount()).max(BigInteger.ZERO));
        return new ChainTransaction("mock_tx_" + UUID.randomUUID().toString().replace("-", ""), 1_000_000L);
    }

    @Override
    public Optional<ChainAccount> getAccount(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(accounts.get(accountId))
                .map(balance -> new ChainAccount(accountId, balance));
    }

    @Override
    public BigInteger readTotalChainBalance() {
        return totalBalance.get();
    }

    @Override
    public ChainTransaction anchorLedgerHash(String sha256Hex, long entryCount) {
        return new ChainTransaction("mock_anchor_" + UUID.randomUUID().toString().replace("-", ""), 800_000L);
    }
}
