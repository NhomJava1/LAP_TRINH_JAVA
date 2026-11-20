package com.octapoint.core.chain;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockChainClientTest {

    @Test
    void mockChainClientImplementsChainClient() {
        ChainClient client = new MockChainClient();
        assertNotNull(client, "MockChainClient should not be null");
    }

    @Test
    void operationsFailExplicitlyUntilImplemented() {
        ChainClient client = new MockChainClient();
        CreateAccountRequest createRequest = new CreateAccountRequest("owner-1");
        BalanceChangeRequest balanceRequest =
            new BalanceChangeRequest("account-1", BigInteger.ONE);

        assertThrows(UnsupportedOperationException.class,
            () -> client.createAccount(createRequest));
        assertThrows(UnsupportedOperationException.class,
            () -> client.mint(balanceRequest));
        assertThrows(UnsupportedOperationException.class,
            () -> client.redeem(balanceRequest));
        assertThrows(UnsupportedOperationException.class,
            () -> client.getAccount("account-1"));
    }
}