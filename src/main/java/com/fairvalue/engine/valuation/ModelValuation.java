package com.fairvalue.engine.valuation;

public record ModelValuation(
        String name,
        double value,
        double weight,
        String rationale
) {
}
