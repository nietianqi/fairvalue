package com.fairvalue.engine.api.dto.jp;

public record JpScreenerItem(
        String code,
        String companyName,
        String industry,
        double currentPrice,
        double fairValueMid,
        double undervaluedPct,
        int confidenceScore,
        double dividendYield
) {
}
