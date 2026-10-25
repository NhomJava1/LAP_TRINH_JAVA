package com.octapoint.core.chain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MockChainClientTest {

    @Test
    void testMockChainClientInitialization() {
        // Kiểm tra khởi tạo khung thô MockChainClient
        MockChainClient client = new MockChainClient();
        assertNotNull(client, "MockChainClient should not be null");
    }
}