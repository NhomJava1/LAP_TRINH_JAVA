package com.octapoint.core.chain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class SignerChainClient implements ChainClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String serviceToken;

    public SignerChainClient(ObjectMapper objectMapper, String baseUrl, String serviceToken) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.serviceToken = serviceToken;
    }

    @Override
    public ChainAccount createAccount(CreateAccountRequest request) {
        return new ChainAccount("sui_acc_" + request.ownerId(), BigInteger.ZERO);
    }

    @Override
    public ChainTransaction mint(BalanceChangeRequest request) {
        return invokeSigner("/internal/issue", request);
    }

    @Override
    public ChainTransaction redeem(BalanceChangeRequest request) {
        return invokeSigner("/internal/redeem", request);
    }

    @Override
    public Optional<ChainAccount> getAccount(String accountId) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(10))
                    .header("X-Service-Token", serviceToken)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return Optional.of(new ChainAccount(accountId, BigInteger.ZERO));
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private ChainTransaction invokeSigner(String path, BalanceChangeRequest request) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("amount", request.amount().longValueExact());
            body.put("accountId", request.accountId());
            String json = objectMapper.writeValueAsString(body);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("X-Service-Token", serviceToken)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Signer HTTP " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String digest = root.path("digest").asText();
            if (digest == null || digest.isBlank()) {
                throw new IllegalStateException("Signer response missing transaction digest");
            }
            return new ChainTransaction(digest);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Signer call failed on " + path + ": " + e.getMessage(), e);
        }
    }
}
