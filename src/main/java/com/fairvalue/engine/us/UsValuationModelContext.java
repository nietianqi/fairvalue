package com.fairvalue.engine.us;

import com.fairvalue.engine.domain.StockSnapshot;

import java.util.List;

public record UsValuationModelContext(
        long securityId,
        StockSnapshot snapshot,
        UsSecurityMaster security,
        UsSecClient.UsSecProfile secProfile,
        UsResolvedValuationConfig config,
        List<UsFinancialStandardizedRecord> financials,
        List<UsFinancialDerivedMetricRecord> derivedMetrics,
        UsFinancialStandardizedRecord latestTtm,
        UsFinancialStandardizedRecord latestAnnual,
        UsFinancialStandardizedRecord latestQuarter,
        UsFinancialDerivedMetricRecord latestDerived,
        UsFinancialQualityScoreRecord latestQualityScore,
        UsDataQualityAuditRecord latestAudit,
        List<UsMarketPriceDailyRecord> dailyHistory,
        List<UsMarketSnapshotRecord> marketSnapshots,
        UsExternalValuationParameterService.ExternalParameterSnapshot externalParameterSnapshot
) {
    public UsValuationModelContext(
            long securityId,
            StockSnapshot snapshot,
            UsSecurityMaster security,
            UsSecClient.UsSecProfile secProfile,
            UsResolvedValuationConfig config,
            List<UsFinancialStandardizedRecord> financials,
            List<UsFinancialDerivedMetricRecord> derivedMetrics,
            UsFinancialStandardizedRecord latestTtm,
            UsFinancialStandardizedRecord latestAnnual,
            UsFinancialStandardizedRecord latestQuarter,
            UsFinancialDerivedMetricRecord latestDerived,
            UsFinancialQualityScoreRecord latestQualityScore,
            UsDataQualityAuditRecord latestAudit,
            List<UsMarketPriceDailyRecord> dailyHistory,
            List<UsMarketSnapshotRecord> marketSnapshots
    ) {
        this(
                securityId,
                snapshot,
                security,
                secProfile,
                config,
                financials,
                derivedMetrics,
                latestTtm,
                latestAnnual,
                latestQuarter,
                latestDerived,
                latestQualityScore,
                latestAudit,
                dailyHistory,
                marketSnapshots,
                null
        );
    }
}
