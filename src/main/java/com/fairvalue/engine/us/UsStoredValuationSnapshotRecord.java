package com.fairvalue.engine.us;

import java.time.Instant;
import java.time.LocalDate;

public record UsStoredValuationSnapshotRecord(
        long securityId,
        String ticker,
        long latestRunId,
        Instant asOfTime,
        double currentPrice,
        double fairValueLow,
        double fairValueMid,
        double fairValueHigh,
        double upsidePct,
        double confidenceLevel,
        double marginOfSafety,
        String finalVerdict,
        String impliedExpectation,
        boolean valueTrapFlag,
        String sectorTemplate,
        String companyType,
        Double qualityScore,
        Double dataQualityScore,
        String dataVersion,
        LocalDate priceAsOf,
        Integer priceFreshnessDays,
        String priceSourceType,
        boolean rankable,
        String valuationStatus,
        String exclusionReason,
        String industryMatchSource,
        Double industryMatchConfidence,
        boolean industryFallbackUsed,
        String summaryJson,
        String reportJson,
        String sourceAttributionJson
) {
}
