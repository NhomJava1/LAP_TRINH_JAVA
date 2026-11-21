package com.octapoint.core.chain;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

public interface ChainClient {

    ChainAccount createAccount(CreateAccountRequest request);

    ChainTransaction mint(BalanceChangeRequest request);

    ChainTransaction redeem(BalanceChangeRequest request);

    Optional<ChainAccount> getAccount(String accountId);

    default ChainTransaction mintBatch(List<BigInteger> amounts) {
        if (amounts == null || amounts.isEmpty()) {
            throw new IllegalArgumentException("amounts must not be empty");
        }
        ChainTransaction last = null;
        for (BigInteger amount : amounts) {
            last = mint(new BalanceChangeRequest("batch_account", amount));
        }
        return last;
    }

    default ChainTransaction mintBatchRequests(List<BalanceChangeRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("requests must not be empty");
        }
        return mintBatch(requests.stream().map(BalanceChangeRequest::amount).toList());
    }

    default BigInteger readTotalChainBalance() {
        return BigInteger.ZERO;
    }

    default ChainTransaction anchorLedgerHash(String sha256Hex, long entryCount) {
        return new ChainTransaction("anchor_unsupported_" + sha256Hex.substring(0, 12), 0L);
    }
}
