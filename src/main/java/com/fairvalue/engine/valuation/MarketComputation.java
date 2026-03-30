package com.fairvalue.engine.valuation;

import java.util.List;
import java.util.Map;

public record MarketComputation(
        List<ModelValuation> models,
        List<MarketAdjustment> adjustments,
        List<String> riskFlags,
        Map<String, Double> drivers,
        double confidenceBase,
        String methodology,
        String modelSelectionReason
) {
}
