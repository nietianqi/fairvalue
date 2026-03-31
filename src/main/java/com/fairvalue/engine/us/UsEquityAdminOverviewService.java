package com.fairvalue.engine.us;

import com.fairvalue.engine.repository.FinancialDerivedMetricsRepository;
import com.fairvalue.engine.repository.FinancialQualityScoresRepository;
import com.fairvalue.engine.repository.FinancialStandardizedRepository;
import com.fairvalue.engine.repository.SourceDocumentFactRawRepository;
import com.fairvalue.engine.repository.SourceDocumentRepository;
import com.fairvalue.engine.repository.SourceRegistryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UsEquityAdminOverviewService {
    private final UsSecurityMasterService usSecurityMasterService;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final SourceDocumentFactRawRepository sourceDocumentFactRawRepository;
    private final FinancialStandardizedRepository financialStandardizedRepository;
    private final FinancialDerivedMetricsRepository financialDerivedMetricsRepository;
    private final FinancialQualityScoresRepository financialQualityScoresRepository;
    private final SourceRegistryRepository sourceRegistryRepository;

    public UsEquityAdminOverviewService(
            UsSecurityMasterService usSecurityMasterService,
            SourceDocumentRepository sourceDocumentRepository,
            SourceDocumentFactRawRepository sourceDocumentFactRawRepository,
            FinancialStandardizedRepository financialStandardizedRepository,
            FinancialDerivedMetricsRepository financialDerivedMetricsRepository,
            FinancialQualityScoresRepository financialQualityScoresRepository,
            SourceRegistryRepository sourceRegistryRepository
    ) {
        this.usSecurityMasterService = usSecurityMasterService;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.sourceDocumentFactRawRepository = sourceDocumentFactRawRepository;
        this.financialStandardizedRepository = financialStandardizedRepository;
        this.financialDerivedMetricsRepository = financialDerivedMetricsRepository;
        this.financialQualityScoresRepository = financialQualityScoresRepository;
        this.sourceRegistryRepository = sourceRegistryRepository;
    }

    public AdminOverview overview(String rawTicker) {
        String ticker = normalizeTicker(rawTicker);
        long securityId = usSecurityMasterService.resolveSecurityId(ticker)
                .orElseThrow(() -> new IllegalStateException("Ticker " + ticker + " does not exist in security master."));
        long companyIrSourceId = sourceRegistryRepository.findIdBySourceName(UsSecurityIdentifierService.COMPANY_IR)
                .orElse(-1L);

        return new AdminOverview(
                ticker,
                securityId,
                sourceDocumentRepository.countBySecurityId(securityId),
                companyIrSourceId < 0 ? 0L : sourceDocumentRepository.countBySecurityIdAndSourceId(securityId, companyIrSourceId),
                sourceDocumentFactRawRepository.countBySecurityId(securityId),
                financialStandardizedRepository.countBySecurityId(securityId),
                financialDerivedMetricsRepository.countBySecurityId(securityId),
                financialQualityScoresRepository.countBySecurityId(securityId),
                sourceDocumentRepository.findLatestBySecurityId(securityId, 5),
                companyIrSourceId < 0 ? List.of() : sourceDocumentRepository.findLatestBySecurityIdAndSourceId(securityId, companyIrSourceId, 5),
                sourceDocumentFactRawRepository.findLatestBySecurityId(securityId, 5),
                financialStandardizedRepository.findLatestBySecurityId(securityId, 5),
                financialDerivedMetricsRepository.findLatestBySecurityId(securityId, 5),
                financialQualityScoresRepository.findLatestBySecurityId(securityId, 5)
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
            long companyIrDocumentCount,
            long rawFactCount,
            long financialStandardizedCount,
            long financialDerivedMetricCount,
            long financialQualityScoreCount,
            List<UsSourceDocument> latestDocuments,
            List<UsSourceDocument> latestCompanyIrDocuments,
            List<UsRawFactEntry> latestRawFacts,
            List<UsFinancialStandardizedRecord> latestFinancialStandardized,
            List<UsFinancialDerivedMetricRecord> latestFinancialDerivedMetrics,
            List<UsFinancialQualityScoreRecord> latestFinancialQualityScores
    ) {
    }
}
