package com.fairvalue.engine.api.dto.cn;

import java.util.List;
import java.util.Map;

public record CnValuationRunResponse(
        String ticker,
        String market,
        String companyType,
        String industryRoute,
        double currentPrice,
        double fundamentalFairValue,
        double fairValueLow,
        double fairValueHigh,
        double marginOfSafetyPrice,
        double sentimentUpperBandPrice,
        String currentPricePositioning,
        List<CnMethodResult> methodResults,
        List<CnScenarioResult> scenarios,
        List<CnRiskAdjustment> riskAdjustments,
        CnOperationZones operationZones,
        int confidenceScore,
        List<String> requiredSections,
        Map<String, Object> summary
) {
}
