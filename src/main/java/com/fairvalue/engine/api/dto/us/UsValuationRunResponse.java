package com.fairvalue.engine.api.dto.us;

import java.util.List;
import java.util.Map;

public record UsValuationRunResponse(
        String ticker,
        List<String> valuationMethods,
        List<UsMethodOutput> methodOutputs,
        List<UsScenarioOutput> scenarioMatrix,
        double blendedIntrinsicValue,
        UsFairValueRange fairValueRange,
        double confidenceLevel,
        double marginOfSafety,
        String impliedExpectation,
        List<UsRiskItem> riskMatrix,
        Map<String, String> explanationBlocks,
        UsValuationSummaryResponse summary,
        UsValuationDecisionResponse decision,
        UsValuationExplanationResponse explanation
) {
}
