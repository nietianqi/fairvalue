package com.fairvalue.engine.us;

import java.time.Instant;

public record UsValuationRunRecord(
        long securityId,
        Instant valuationDate,
        String runMode,
        double currentPrice,
        double fairValueLow,
        double fairValueMid,
        double fairValueHigh,
        double blendedIntrinsicValue,
        double confidenceLevel,
        double marginOfSafety,
        String impliedExpectationLabel,
        String sectorTemplate,
        String companyType,
        double weightedValue,
        String finalVerdict,
        double buyZoneLow,
        double buyZoneHigh,
        double holdZoneLow,
        double holdZoneHigh,
        double avoidZoneLow,
        double avoidZoneHigh,
        String reportJson
) {
}
