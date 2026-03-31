package com.fairvalue.engine.us;

import java.math.BigDecimal;

public record UsReverseDcfResultRecord(
        long valuationRunId,
        BigDecimal impliedRevenueCagr,
        BigDecimal impliedEbitdaMargin,
        BigDecimal impliedFcfMargin,
        BigDecimal terminalGrowth,
        BigDecimal wacc,
        String impliedExpectationLabel,
        String notesJson
) {
}
