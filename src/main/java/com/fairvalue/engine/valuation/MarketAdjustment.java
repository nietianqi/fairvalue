package com.fairvalue.engine.valuation;

public record MarketAdjustment(
        String name,
        double impact,
        String rationale
) {
}
