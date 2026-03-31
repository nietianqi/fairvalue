package com.fairvalue.engine.api.dto.us;

import java.util.List;
import java.util.Map;

public record UsValuationDecisionResponse(
        String ticker,
        double currentPrice,
        UsFairValueRange fairValueRange,
        double confidenceLevel,
        double marginOfSafety,
        String impliedExpectation,
        boolean valueTrapFlag,
        List<String> valuationMethods,
        List<UsScenarioOutput> scenarioMatrix,
        List<UsRiskItem> riskMatrix,
        Map<String, Object> dataQualityAudit,
        Map<String, Object> sourceAttribution
) {
}
