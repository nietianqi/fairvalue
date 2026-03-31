package com.fairvalue.engine.api.dto.us;

import java.util.List;
import java.util.Map;

public record UsFinancialQualityResponse(
        String ticker,
        Map<String, Double> profitability5yTtm,
        double earningsQualityScore,
        double revenueQualityScore,
        double balanceSheetScore,
        double capitalEfficiencyScore,
        double capitalAllocationScore,
        double totalQualityScore,
        List<String> redFlags,
        double altmanZ,
        double ownerEarnings
) {
}
