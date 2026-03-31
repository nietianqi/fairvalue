package com.fairvalue.engine.us;

import com.fairvalue.engine.repository.DataQualityAuditRepository;
import com.fairvalue.engine.repository.FinancialDerivedMetricsRepository;
import com.fairvalue.engine.repository.FinancialQualityScoresRepository;
import com.fairvalue.engine.repository.FinancialStandardizedRepository;
import com.fairvalue.engine.repository.MarketDataRawRepository;
import com.fairvalue.engine.repository.MarketIntradaySnapshotRepository;
import com.fairvalue.engine.repository.MarketPriceDailyRepository;
import com.fairvalue.engine.repository.MarketSnapshotRepository;
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
    private final DataQualityAuditRepository dataQualityAuditRepository;
    private final MarketDataRawRepository marketDataRawRepository;
    private final MarketPriceDailyRepository marketPriceDailyRepository;
    private final MarketSnapshotRepository marketSnapshotRepository;
    private final MarketIntradaySnapshotRepository marketIntradaySnapshotRepository;
    private final SourceRegistryRepository sourceRegistryRepository;

    public UsEquityAdminOverviewService(
            UsSecurityMasterService usSecurityMasterService,
            SourceDocumentRepository sourceDocumentRepository,
            SourceDocumentFactRawRepository sourceDocumentFactRawRepository,
            FinancialStandardizedRepository financialStandardizedRepository,
            FinancialDerivedMetricsRepository financialDerivedMetricsRepository,
            FinancialQualityScoresRepository financialQualityScoresRepository,
            DataQualityAuditRepository dataQualityAuditRepository,
            MarketDataRawRepository marketDataRawRepository,
            MarketPriceDailyRepository marketPriceDailyRepository,
            MarketSnapshotRepository marketSnapshotRepository,
            MarketIntradaySnapshotRepository marketIntradaySnapshotRepository,
            SourceRegistryRepository sourceRegistryRepository
    ) {
        this.usSecurityMasterService = usSecurityMasterService;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.sourceDocumentFactRawRepository = sourceDocumentFactRawRepository;
        this.financialStandardizedRepository = financialStandardizedRepository;
        this.financialDerivedMetricsRepository = financialDerivedMetricsRepository;
        this.financialQualityScoresRepository = financialQualityScoresRepository;
        this.dataQualityAuditRepository = dataQualityAuditRepository;
        this.marketDataRawRepository = marketDataRawRepository;
        this.marketPriceDailyRepository = marketPriceDailyRepository;
        this.marketSnapshotRepository = marketSnapshotRepository;
        this.marketIntradaySnapshotRepository = marketIntradaySnapshotRepository;
        this.sourceRegistryRepository = sourceRegistryRepository;
    }

    public AdminOverview overview(String rawTicker) {
        String ticker = normalizeTicker(rawTicker);
        long securityId = usSecurityMasterService.resolveSecurityId(ticker)
                .orElseThrow(() -> new IllegalStateException("Ticker " + ticker + " does not exist in security master."));
        long companyIrSourceId = sourceRegistryRepository.findIdBySourceName(UsSecurityIdentifierService.COMPANY_IR)
                .orElse(-1L);
        UsSecurityMaster securityMaster = usSecurityMasterService.findById(securityId).orElse(null);

        return new AdminOverview(
                ticker,
                securityId,
                securityMaster == null ? null : securityMaster.companyName(),
                securityMaster == null ? null : securityMaster.sector(),
                securityMaster == null ? null : securityMaster.industry(),
                securityMaster == null ? null : securityMaster.companyType(),
                securityMaster == null ? null : securityMaster.sectorTemplate(),
                sourceDocumentRepository.countBySecurityId(securityId),
                companyIrSourceId < 0 ? 0L : sourceDocumentRepository.countBySecurityIdAndSourceId(securityId, companyIrSourceId),
                sourceDocumentFactRawRepository.countBySecurityId(securityId),
                financialStandardizedRepository.countBySecurityId(securityId),
                financialDerivedMetricsRepository.countBySecurityId(securityId),
                financialQualityScoresRepository.countBySecurityId(securityId),
                dataQualityAuditRepository.countBySecurityId(securityId),
                marketDataRawRepository.countBySecurityId(securityId),
                marketPriceDailyRepository.countBySecurityId(securityId),
                marketSnapshotRepository.countBySecurityId(securityId),
                marketIntradaySnapshotRepository.countBySecurityId(securityId),
                sourceDocumentRepository.findLatestBySecurityId(securityId, 5),
                companyIrSourceId < 0 ? List.of() : sourceDocumentRepository.findLatestBySecurityIdAndSourceId(securityId, companyIrSourceId, 5),
                sourceDocumentFactRawRepository.findLatestBySecurityId(securityId, 5),
                financialStandardizedRepository.findLatestBySecurityId(securityId, 5),
                financialDerivedMetricsRepository.findLatestBySecurityId(securityId, 5),
                financialQualityScoresRepository.findLatestBySecurityId(securityId, 5),
                dataQualityAuditRepository.findLatestBySecurityId(securityId, 5)
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
            String companyName,
            String sector,
            String industry,
            String companyType,
            String sectorTemplate,
            long sourceDocumentCount,
            long companyIrDocumentCount,
            long rawFactCount,
            long financialStandardizedCount,
            long financialDerivedMetricCount,
            long financialQualityScoreCount,
            long dataQualityAuditCount,
            long marketDataRawCount,
            long marketPriceDailyCount,
            long marketSnapshotCount,
            long marketIntradaySnapshotCount,
            List<UsSourceDocument> latestDocuments,
            List<UsSourceDocument> latestCompanyIrDocuments,
            List<UsRawFactEntry> latestRawFacts,
            List<UsFinancialStandardizedRecord> latestFinancialStandardized,
            List<UsFinancialDerivedMetricRecord> latestFinancialDerivedMetrics,
            List<UsFinancialQualityScoreRecord> latestFinancialQualityScores,
            List<UsDataQualityAuditRecord> latestDataQualityAudits
    ) {
    }
}
