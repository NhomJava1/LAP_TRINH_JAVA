package com.octapoint.core.chain;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockChainClientTest {

    @Test
    void mockChainClientImplementsChainClient() {
        ChainClient client = new MockChainClient();
        assertNotNull(client, "MockChainClient should not be null");
    }

    @Test
    void operationsTrackMockBalanceAndValidateInsufficientRedeem() {
        ChainClient client = new MockChainClient();
        ChainAccount account = client.createAccount(new CreateAccountRequest("owner-1"));
        assertEquals("mock_acc_owner-1", account.accountId());
        assertEquals(BigInteger.ZERO, account.balance());

        ChainTransaction mintTx = client.mint(new BalanceChangeRequest(account.accountId(), BigInteger.valueOf(100)));
        assertTrue(mintTx.digest().startsWith("mock_tx_"));

        ChainTransaction redeemTx = client.redeem(new BalanceChangeRequest(account.accountId(), BigInteger.valueOf(30)));
        assertTrue(redeemTx.digest().startsWith("mock_tx_"));

        ChainAccount updated = client.getAccount(account.accountId()).orElseThrow();
        assertEquals(BigInteger.valueOf(70), updated.balance());

        assertThrows(IllegalStateException.class,
                () -> client.redeem(new BalanceChangeRequest(account.accountId(), BigInteger.valueOf(100))));
        assertFalse(client.getAccount("non-existent").isPresent());
    }
}
