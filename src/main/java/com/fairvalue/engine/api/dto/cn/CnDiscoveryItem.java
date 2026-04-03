package com.fairvalue.engine.api.dto.cn;

public record CnDiscoveryItem(
        String ticker,
        String name,
        String industry,
        double price,
        double fairValue,
        double fairValueLow,
        double fairValueHigh,
        double upside,
        int confidenceScore,
        String verdict,
        String financialHealth,
        String cashflowRating,
        String growthRating,
        String profitabilityRating,
        String analystRating,
        double pe,
        double evEbitda,
        double marketCap,
        double eps5y,
        double dailyChange,
        double turnover
) {
}
