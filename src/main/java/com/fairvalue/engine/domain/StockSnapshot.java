package com.fairvalue.engine.domain;

public record StockSnapshot(
        Market market,
        String symbol,
        String currency,
        String companyName,
        String industry,
        double price,
        StockFundamentals fundamentals,
        String dataVersion
) {
}
