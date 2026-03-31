package com.fairvalue.engine.us;

public record UsReverseDcfAnalysis(
        double impliedRevenueCagr,
        double impliedEbitdaMargin,
        double impliedFcfMargin,
        double effectiveWacc,
        double terminalGrowth,
        String impliedExpectationLabel,
        String notesJson
) {
}
