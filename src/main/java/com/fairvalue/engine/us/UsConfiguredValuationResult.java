package com.fairvalue.engine.us;

import com.fairvalue.engine.valuation.MarketAdjustment;

import java.util.List;
import java.util.Map;

public record UsConfiguredValuationResult(
        List<UsConfiguredMethodValuation> methods,
        UsReverseDcfAnalysis reverseDcfAnalysis,
        UsRiskMatrixResult riskMatrixResult,
        Map<String, Double> drivers,
        List<MarketAdjustment> adjustments,
        List<String> riskFlags,
        double confidenceBase,
        double requiredMarginOfSafety,
        String methodology,
        String modelSelectionReason
) {
}
