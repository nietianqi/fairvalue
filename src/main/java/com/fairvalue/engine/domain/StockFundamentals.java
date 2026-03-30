package com.fairvalue.engine.domain;

public record StockFundamentals(
        double revenueGrowth,
        double fcfMargin,
        double wacc,
        double terminalGrowth,
        double roe,
        double pe,
        double pb,
        double evEbitda,
        double dividendYield,
        double buybackYield,
        double netCashToMarketCap,
        double policySensitivity,
        double themePremium,
        double liquidityScore,
        double ahPremiumGap,
        double sbcRatio,
        double earningsVolatility,
        double dataFreshnessDays,
        double dataCompleteness,
        double analystCoverage,
        double chinaExposure,
        double governanceScore,
        boolean positiveFreeCashFlow
) {
}
