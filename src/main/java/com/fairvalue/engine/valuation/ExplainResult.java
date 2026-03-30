package com.fairvalue.engine.valuation;

import java.util.Map;

public record ExplainResult(
        String market,
        String symbol,
        String methodology,
        String modelSelectionReason,
        Map<String, Double> topDrivers,
        Map<String, Double> confidenceBreakdown,
        java.util.List<ModelValuation> models,
        java.util.List<MarketAdjustment> marketAdjustments,
        java.util.List<String> riskFlags
) {
}
