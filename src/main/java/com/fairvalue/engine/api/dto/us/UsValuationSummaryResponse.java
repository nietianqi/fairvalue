package com.fairvalue.engine.api.dto.us;

import java.time.LocalDate;

public record UsValuationSummaryResponse(
        String ticker,
        String verdict,
        UsFairValueRange fairValueRange,
        String currentAssessment,
        double upsideDownside,
        double confidenceLevel,
        UsFairValueRange buyZone,
        UsFairValueRange holdZone,
        UsFairValueRange avoidZone,
        double currentPrice,
        String dataVersion,
        String impliedExpectation,
        LocalDate priceAsOf,
        Integer priceFreshnessDays,
        String priceSourceType,
        Boolean rankable,
        String valuationStatus,
        String exclusionReason
) {
}
