package com.octapoint.core.anchor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.octapoint.core.audit.AuditService;
import com.octapoint.core.chain.ChainClient;
import com.octapoint.core.chain.ChainTransaction;
import com.octapoint.core.ledger.LedgerEntry;
import com.octapoint.core.ledger.LedgerEntryRepository;
import com.octapoint.core.shared.HmacCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnchorService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerAnchorRepository anchorRepository;
    private final ChainClient chainClient;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final boolean walrusPocEnabled;
    private final String walrusPublisherUrl;
    private final HttpClient httpClient;

    public AnchorService(
            LedgerEntryRepository ledgerEntryRepository,
            LedgerAnchorRepository anchorRepository,
            ChainClient chainClient,
            AuditService auditService,
            ObjectMapper objectMapper,
            @Value("${app.walrus.poc-enabled:false}") boolean walrusPocEnabled,
            @Value("${app.walrus.publisher-url:https://publisher.walrus-testnet.walrus.space}") String walrusPublisherUrl) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.anchorRepository = anchorRepository;
        this.chainClient = chainClient;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.walrusPocEnabled = walrusPocEnabled;
        this.walrusPublisherUrl = walrusPublisherUrl.replaceAll("/+$", "");
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public LedgerAnchor anchorCurrentLedger(String actor) {
        List<LedgerEntry> entries = ledgerEntryRepository.findAllByOrderByCreatedAtAsc();
        StringBuilder canonical = new StringBuilder();
        for (LedgerEntry e : entries) {
            canonical.append(e.getId()).append('|')
                    .append(e.getTenantId()).append('|')
                    .append(e.getCustomerId()).append('|')
                    .append(e.getType()).append('|')
                    .append(e.getAmount() != null ? e.getAmount().longValue() : 0L).append('|')
                    .append(e.getChainSeq()).append('\n');
        }
        String batchHash = HmacCodec.sha256Hex(canonical.toString());

        ChainTransaction tx = chainClient.anchorLedgerHash(batchHash, entries.size());
        String walrusBlobId = walrusPocEnabled ? tryUploadToWalrus(batchHash, entries.size()) : null;

        LedgerAnchor anchor = new LedgerAnchor();
        anchor.setId("anc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        anchor.setEntryCount((long) entries.size());
        anchor.setBatchSha256Hash(batchHash);
        anchor.setSuiTxDigest(tx.digest());
        anchor.setWalrusBlobId(walrusBlobId);
        anchor.setStatus("ANCHORED");
        LedgerAnchor saved = anchorRepository.save(anchor);

        auditService.log(null, actor, "ADMIN", "LEDGER_HASH_ANCHORED", "LEDGER_ANCHOR",
                saved.getId(), "SUCCESS",
                "Anchored " + entries.size() + " entries, sha256=" + batchHash + ", digest=" + tx.digest(),
                saved.getId());
        return saved;
    }

    public List<LedgerAnchor> listRecent() {
        return anchorRepository.findTop20ByOrderByCreatedAtDesc();
    }

    private String tryUploadToWalrus(String batchHash, long entryCount) {
        try {
            String proofJson = objectMapper.writeValueAsString(Map.of(
                    "project", "OctaPoint",
                    "batchSha256Hash", batchHash,
                    "entryCount", entryCount,
                    "timestamp", Instant.now().toString()
            ));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(walrusPublisherUrl + "/v1/blobs?epochs=1"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(proofJson))
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(res.body());
                JsonNode newlyCreated = root.path("newlyCreated").path("blobObject").path("blobId");
                if (!newlyCreated.isMissingNode() && !newlyCreated.asText().isBlank()) {
                    return newlyCreated.asText();
                }
                JsonNode alreadyCertified = root.path("alreadyCertified").path("blobId");
                if (!alreadyCertified.isMissingNode() && !alreadyCertified.asText().isBlank()) {
                    return alreadyCertified.asText();
                }
            }
        } catch (Exception ignored) {
            // Optional PoC adapter must never fail primary Sui anchor
        }
        return null;
    }
}
