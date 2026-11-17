package com.octapoint.core.reconcile;

import com.octapoint.core.audit.AuditService;
import com.octapoint.core.chain.ChainClient;
import com.octapoint.core.ledger.LedgerEntry;
import com.octapoint.core.ledger.LedgerEntryRepository;
import com.octapoint.core.outbox.OutboxEvent;
import com.octapoint.core.outbox.OutboxEventRepository;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReconciliationService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ReconciliationReportRepository reportRepository;
    private final ChainClient chainClient;
    private final AuditService auditService;

    public ReconciliationService(
            LedgerEntryRepository ledgerEntryRepository,
            OutboxEventRepository outboxEventRepository,
            ReconciliationReportRepository reportRepository,
            ChainClient chainClient,
            AuditService auditService) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.reportRepository = reportRepository;
        this.chainClient = chainClient;
        this.auditService = auditService;
    }

    public ReconciliationReport runReconciliation(String actor) {
        List<OutboxEvent> settled = outboxEventRepository.findByStatus("SETTLED");
        Map<String, LedgerEntry> entriesById = ledgerEntryRepository.findAll().stream()
                .collect(Collectors.toMap(LedgerEntry::getId, e -> e, (a, b) -> a));

        long dbIssued = 0L;
        long dbRedeemed = 0L;
        for (OutboxEvent evt : settled) {
            LedgerEntry entry = entriesById.get(evt.getAggregateId());
            if (entry == null || entry.getAmount() == null) continue;
            long pts = entry.getAmount().longValue();
            if ("ISSUE".equals(entry.getType())) {
                dbIssued += pts;
            } else if ("REDEEM".equals(entry.getType())) {
                dbRedeemed += pts;
            }
        }

        BigInteger onChainRaw = chainClient.readTotalChainBalance();
        long onChain = onChainRaw != null ? onChainRaw.longValue() : 0L;
        long netDbBalance = dbIssued - dbRedeemed;
        long delta = onChain - dbIssued;

        String status;
        String details;
        if (onChain == netDbBalance || delta == 0L) {
            status = "MATCHED";
            details = "PostgreSQL settled points match on-chain balance exactly.";
        } else if (onChain > dbIssued) {
            status = "MATCHED_WITH_HISTORY";
            details = "Shared Sui Balance object contains " + onChain
                    + " total points (includes historical sprint issues; current DB settled issues=" + dbIssued + ").";
        } else {
            status = "MISMATCH_ALERT";
            details = "On-chain balance (" + onChain + ") is lower than DB settled issues (" + dbIssued + ").";
        }

        ReconciliationReport report = new ReconciliationReport();
        report.setId("rec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        report.setDbSettledIssuePoints(dbIssued);
        report.setDbSettledRedeemPoints(dbRedeemed);
        report.setChainBalanceValue(onChain);
        report.setDeltaPoints(delta);
        report.setStatus(status);
        report.setDetails(details);
        ReconciliationReport saved = reportRepository.save(report);

        auditService.log(null, actor, "ADMIN", "RECONCILIATION_RUN", "RECONCILIATION",
                saved.getId(), "SUCCESS", status + ": " + details, saved.getId());
        return saved;
    }

    public List<ReconciliationReport> listRecent() {
        return reportRepository.findTop20ByOrderByCreatedAtDesc();
    }
}
