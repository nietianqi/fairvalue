package com.fairvalue.engine.api.dto.cn;

import java.util.List;
import java.util.Map;

public record CnValuationReportResponse(
        String ticker,
        String market,
        String oneLineVerdict,
        List<CnMethodResult> valuationMethods,
        List<CnScenarioResult> scenarioMatrix,
        List<CnRiskAdjustment> riskMatrix,
        CnOperationZones operationZones,
        int confidenceScore,
        Map<String, String> sections
) {
}
