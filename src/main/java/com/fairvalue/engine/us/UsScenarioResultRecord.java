package com.fairvalue.engine.us;

import java.math.BigDecimal;

public record UsScenarioResultRecord(
        long valuationRunId,
        String scenarioName,
        double probabilityWeight,
        double priceTarget,
        BigDecimal revenueCagr,
        BigDecimal ebitdaMargin,
        BigDecimal fcfMargin,
        String notesJson
) {
}
