package com.octapoint.core.chain;

import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class MockChainClient implements ChainClient {

    @Override
    public ChainAccount createAccount(CreateAccountRequest request) {
        throw unsupported("create account");
    }

    @Override
    public ChainTransaction mint(BalanceChangeRequest request) {
        throw unsupported("mint");
    }

    @Override
    public ChainTransaction redeem(BalanceChangeRequest request) {
        throw unsupported("redeem");
    }

    @Override
    public Optional<ChainAccount> getAccount(String accountId) {
        throw unsupported("read account");
    }

    private UnsupportedOperationException unsupported(String operation) {
        return new UnsupportedOperationException(operation + " is not implemented");
    }
}