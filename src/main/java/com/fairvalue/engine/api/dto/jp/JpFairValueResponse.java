package com.fairvalue.engine.api.dto.jp;

import java.util.List;
import java.util.Map;

public record JpFairValueResponse(
        String code,
        String companyName,
        String market,
        String currency,
        String industry,
        double currentPrice,
        double fairValueLow,
        double fairValueBase,
        double fairValueHigh,
        double fairValueMid,
        String valuationLabel,
        int confidenceScore,
        String confidenceLevel,
        double upsideDownsidePct,
        double marginOfSafety,
        List<String> methodsUsed,
        List<JpMethodResult> methodResults,
        List<JpScenarioResult> scenarios,
        Map<String, Double> weights,
        Map<String, Double> assumptions,
        List<JpRiskFlag> riskFlags,
        List<JpCatalystFlag> catalystFlags,
        String dataVersion
) {
}
