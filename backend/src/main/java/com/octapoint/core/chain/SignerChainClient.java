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
import java.util.List;
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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", request.amount().longValueExact());
        body.put("accountId", request.accountId());
        return postSignerTx("/internal/issue", body);
    }

    @Override
    public ChainTransaction mintBatch(List<BigInteger> amounts) {
        if (amounts == null || amounts.isEmpty()) {
            throw new IllegalArgumentException("amounts must not be empty");
        }
        List<Long> longAmounts = amounts.stream().map(BigInteger::longValueExact).toList();
        return postSignerTx("/internal/batch-issue", Map.of("amounts", longAmounts));
    }

    @Override
    public ChainTransaction redeem(BalanceChangeRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", request.amount().longValueExact());
        body.put("accountId", request.accountId());
        return postSignerTx("/internal/redeem", body);
    }

    @Override
    public BigInteger readTotalChainBalance() {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/internal/balance"))
                    .timeout(Duration.ofSeconds(15))
                    .header("X-Service-Token", serviceToken)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Signer balance HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode bal = root.path("onChainBalance");
            if (bal.isNull() || bal.isMissingNode()) {
                throw new IllegalStateException("Signer balance response missing onChainBalance");
            }
            return BigInteger.valueOf(bal.asLong());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read on-chain balance: " + e.getMessage(), e);
        }
    }

    @Override
    public ChainTransaction anchorLedgerHash(String sha256Hex, long entryCount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("batchHash", sha256Hex);
        body.put("entryCount", entryCount);
        return postSignerTx("/internal/anchor", body);
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

    private ChainTransaction postSignerTx(String path, Map<String, Object> body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(25))
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
            long gasMist = root.path("gasMist").asLong(0L);
            return new ChainTransaction(digest, Math.max(0L, gasMist));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Signer call failed on " + path + ": " + e.getMessage(), e);
        }
    }
}
