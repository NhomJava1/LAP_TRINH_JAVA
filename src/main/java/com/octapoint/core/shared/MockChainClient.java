package com.octapoint.core.shared;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class MockChainClient implements ChainClient {
    @Override
    public String issueCreditOnChain(String userId, long amount) {
        return "0xMOCK_SUI_TX_" + UUID.randomUUID().toString();
    }
}