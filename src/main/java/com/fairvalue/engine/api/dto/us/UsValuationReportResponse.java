package com.fairvalue.engine.api.dto.us;

import java.util.List;
import java.util.Map;

public record UsValuationReportResponse(
        String ticker,
        String oneLineVerdict,
        String executiveSummary,
        Map<String, Object> dataQualityAudit,
        Map<String, Object> businessAndMoat,
        Map<String, Object> financialQualityScorecard,
        Map<String, Object> usMarketModifiers,
        List<UsRiskItem> riskMatrix,
        List<UsMethodOutput> valuationBreakdown,
        List<UsScenarioOutput> scenarioMatrix,
        UsFairValueRange finalFairValue,
        Map<String, String> actionableFramework,
        String uncertaintyAndErrorSources,
        UsValuationSummaryResponse summary,
        UsValuationDecisionResponse decision,
        UsValuationExplanationResponse explanation
) {
}
