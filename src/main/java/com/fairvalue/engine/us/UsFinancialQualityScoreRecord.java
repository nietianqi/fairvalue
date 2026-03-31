package com.fairvalue.engine.us;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UsFinancialQualityScoreRecord(
        long securityId,
        String periodType,
        LocalDate periodEnd,
        BigDecimal earningsQualityScore,
        BigDecimal revenueQualityScore,
        BigDecimal balanceSheetScore,
        BigDecimal capitalEfficiencyScore,
        BigDecimal capitalAllocationScore,
        BigDecimal totalQualityScore,
        String qualityScoreBreakdownJson,
        String redFlagsJson,
        String forensicFlagsJson
) {
}
