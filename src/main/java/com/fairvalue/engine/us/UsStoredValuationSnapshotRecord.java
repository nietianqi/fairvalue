package com.fairvalue.engine.us;

import java.time.Instant;

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
        String summaryJson,
        String reportJson,
        String sourceAttributionJson
) {
}
