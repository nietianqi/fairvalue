package com.fairvalue.engine.api.dto.jp;

public record JpStockOverviewResponse(
        String code,
        String companyName,
        String market,
        String currency,
        double currentPrice,
        double fairValueMid,
        double fairValueLow,
        double fairValueHigh,
        String valuationLabel,
        String confidenceLevel,
        double upsideDownsidePct
) {
}
