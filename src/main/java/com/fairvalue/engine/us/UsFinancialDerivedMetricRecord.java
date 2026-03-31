package com.fairvalue.engine.us;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UsFinancialDerivedMetricRecord(
        long securityId,
        String periodType,
        Integer fiscalYear,
        String fiscalPeriod,
        LocalDate periodEnd,
        BigDecimal grossMargin,
        BigDecimal ebitMargin,
        BigDecimal fcfMargin,
        BigDecimal roe,
        BigDecimal roic,
        BigDecimal roa,
        BigDecimal fcfConversion,
        BigDecimal accrualsRatio,
        BigDecimal netDebtToEbitda,
        BigDecimal interestCoverage,
        BigDecimal workingCapitalRatio,
        BigDecimal bookValuePerShare,
        BigDecimal epsDiluted,
        BigDecimal ownerEarningsEstimate,
        BigDecimal altmanZScore
) {
}
