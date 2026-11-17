package com.octapoint.core.chain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChainClientConfig {

    @Bean
    public ChainClient chainClient(
            ObjectMapper objectMapper,
            @Value("${app.settlement.mode:LIVE}") String settlementMode,
            @Value("${app.signer.base-url:http://signer:3001}") String signerBaseUrl,
            @Value("${app.signer.service-token:replace-with-random-internal-token}") String signerServiceToken) {
        if ("LIVE".equalsIgnoreCase(settlementMode)) {
            return new SignerChainClient(objectMapper, signerBaseUrl, signerServiceToken);
        }
        return new MockChainClient();
    }
}
