package com.fairvalue.engine.api.dto.cn;

public record CnValuationSummaryResponse(
        String ticker,
        String market,
        double currentPrice,
        double fairValueMid,
        double fairValueLow,
        double fairValueHigh,
        double upside,
        int confidenceScore,
        String verdict
) {
}
