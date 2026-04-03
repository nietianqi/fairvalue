package com.fairvalue.engine.api.dto;

public record MarketRankingItem(
        String ticker,
        String companyName,
        String industry,
        Double currentPrice,
        Double fairValue,
        Double fairValueLow,
        Double fairValueHigh,
        Double upsidePct,
        Double confidenceScore,
        String verdict,
        Double peTtm,
        Double evEbitda,
        Double marketCap,
        Double growthMetric,
        Double dailyChangePct,
        Double activityMetric
) {
}
