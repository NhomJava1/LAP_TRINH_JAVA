package com.octapoint.core.chain;

import java.util.Optional;

public interface ChainClient {

    ChainAccount createAccount(CreateAccountRequest request);

    ChainTransaction mint(BalanceChangeRequest request);

    ChainTransaction redeem(BalanceChangeRequest request);

    Optional<ChainAccount> getAccount(String accountId);
}
