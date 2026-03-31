package com.fairvalue.engine.us;

import com.fairvalue.engine.repository.FinancialDerivedMetricsRepository;
import com.fairvalue.engine.repository.FinancialStandardizedRepository;
import com.fairvalue.engine.repository.SourceDocumentFactRawRepository;
import com.fairvalue.engine.repository.SourceDocumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UsEquityAdminOverviewService {
    private final UsSecurityMasterService usSecurityMasterService;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final SourceDocumentFactRawRepository sourceDocumentFactRawRepository;
    private final FinancialStandardizedRepository financialStandardizedRepository;
    private final FinancialDerivedMetricsRepository financialDerivedMetricsRepository;

    public UsEquityAdminOverviewService(
            UsSecurityMasterService usSecurityMasterService,
            SourceDocumentRepository sourceDocumentRepository,
            SourceDocumentFactRawRepository sourceDocumentFactRawRepository,
            FinancialStandardizedRepository financialStandardizedRepository,
            FinancialDerivedMetricsRepository financialDerivedMetricsRepository
    ) {
        this.usSecurityMasterService = usSecurityMasterService;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.sourceDocumentFactRawRepository = sourceDocumentFactRawRepository;
        this.financialStandardizedRepository = financialStandardizedRepository;
        this.financialDerivedMetricsRepository = financialDerivedMetricsRepository;
    }

    public AdminOverview overview(String rawTicker) {
        String ticker = normalizeTicker(rawTicker);
        long securityId = usSecurityMasterService.resolveSecurityId(ticker)
                .orElseThrow(() -> new IllegalStateException("Ticker " + ticker + " does not exist in security master."));

        return new AdminOverview(
                ticker,
                securityId,
                sourceDocumentRepository.countBySecurityId(securityId),
                sourceDocumentFactRawRepository.countBySecurityId(securityId),
                financialStandardizedRepository.countBySecurityId(securityId),
                financialDerivedMetricsRepository.countBySecurityId(securityId),
                sourceDocumentRepository.findLatestBySecurityId(securityId, 5),
                sourceDocumentFactRawRepository.findLatestBySecurityId(securityId, 5),
                financialStandardizedRepository.findLatestBySecurityId(securityId, 5),
                financialDerivedMetricsRepository.findLatestBySecurityId(securityId, 5)
        );
    }

    private String normalizeTicker(String rawTicker) {
        return rawTicker.trim()
                .toUpperCase(java.util.Locale.ROOT)
                .replace(".US", "")
                .replace(".", "-");
    }

    public record AdminOverview(
            String ticker,
            long securityId,
            long sourceDocumentCount,
            long rawFactCount,
            long financialStandardizedCount,
            long financialDerivedMetricCount,
            List<UsSourceDocument> latestDocuments,
            List<UsRawFactEntry> latestRawFacts,
            List<UsFinancialStandardizedRecord> latestFinancialStandardized,
            List<UsFinancialDerivedMetricRecord> latestFinancialDerivedMetrics
    ) {
    }
}
