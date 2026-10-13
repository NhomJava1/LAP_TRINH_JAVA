package com.octapoint.core.shared;

public interface ChainClient {
    String issueCreditOnChain(String userId, long amount);
}