package com.fairvalue.engine.us;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UsFinancialStandardizedRecord(
        long securityId,
        String periodType,
        Integer fiscalYear,
        String fiscalPeriod,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal revenue,
        BigDecimal grossProfit,
        BigDecimal ebitda,
        BigDecimal ebit,
        BigDecimal netIncome,
        BigDecimal operatingCashFlow,
        BigDecimal capex,
        BigDecimal freeCashFlow,
        BigDecimal cash,
        BigDecimal shortTermDebt,
        BigDecimal longTermDebt,
        BigDecimal totalDebt,
        BigDecimal equity,
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        BigDecimal dilutedShares,
        BigDecimal basicShares,
        BigDecimal sbc,
        BigDecimal leaseLiabilities,
        BigDecimal pensionLiabilities,
        BigDecimal minorityInterest,
        BigDecimal goodwill,
        BigDecimal intangibles,
        BigDecimal taxRateEffective,
        BigDecimal netDebt,
        long sourceDocumentId,
        String qualityFlagJson
) {
}
