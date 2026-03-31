package com.fairvalue.engine.api.dto.us;

public record UsValuationSummaryResponse(
        String ticker,
        String verdict,
        UsFairValueRange fairValueRange,
        String currentAssessment,
        double upsideDownside,
        double confidenceLevel,
        UsFairValueRange buyZone,
        UsFairValueRange holdZone,
        UsFairValueRange avoidZone
) {
}
