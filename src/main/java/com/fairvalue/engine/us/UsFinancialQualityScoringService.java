package com.fairvalue.engine.us;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairvalue.engine.repository.FinancialQualityScoresRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UsFinancialQualityScoringService {
    private final FinancialQualityScoresRepository financialQualityScoresRepository;
    private final ObjectMapper objectMapper;

    public UsFinancialQualityScoringService(
            FinancialQualityScoresRepository financialQualityScoresRepository,
            ObjectMapper objectMapper
    ) {
        this.financialQualityScoresRepository = financialQualityScoresRepository;
        this.objectMapper = objectMapper;
    }

    public int refreshForSecurity(
            long securityId,
            List<UsFinancialStandardizedRecord> standardizedRows,
            List<UsFinancialDerivedMetricRecord> derivedRows
    ) {
        Map<PeriodKey, UsFinancialDerivedMetricRecord> derivedByKey = new LinkedHashMap<>();
        for (UsFinancialDerivedMetricRecord derivedRow : derivedRows) {
            derivedByKey.put(new PeriodKey(
                    derivedRow.periodType(),
                    derivedRow.fiscalYear(),
                    derivedRow.fiscalPeriod(),
                    derivedRow.periodEnd()
            ), derivedRow);
        }

        Map<PeriodLookupKey, UsFinancialStandardizedRecord> standardizedIndex = new LinkedHashMap<>();
        for (UsFinancialStandardizedRecord row : standardizedRows) {
            standardizedIndex.put(new PeriodLookupKey(row.periodType(), row.fiscalYear(), row.fiscalPeriod()), row);
        }

        Map<QualityPeriodKey, UsFinancialQualityScoreRecord> dedupedScores = new LinkedHashMap<>();
        for (UsFinancialStandardizedRecord row : standardizedRows.stream()
                .sorted(Comparator.comparing(UsFinancialStandardizedRecord::periodEnd))
                .toList()) {
            UsFinancialDerivedMetricRecord derived = derivedByKey.get(new PeriodKey(
                    row.periodType(),
                    row.fiscalYear(),
                    row.fiscalPeriod(),
                    row.periodEnd()
            ));
            if (!shouldScore(row, derived)) {
                continue;
            }
            UsFinancialQualityScoreRecord candidate = scoreRow(securityId, row, derived, standardizedIndex);
            QualityPeriodKey key = new QualityPeriodKey(candidate.periodType(), candidate.periodEnd());
            dedupedScores.merge(key, candidate, this::preferRecord);
        }

        List<UsFinancialQualityScoreRecord> scores = dedupedScores.values().stream()
                .sorted(Comparator.comparing(UsFinancialQualityScoreRecord::periodEnd)
                        .thenComparing(UsFinancialQualityScoreRecord::periodType))
                .toList();
        financialQualityScoresRepository.replaceBySecurityId(securityId, scores);
        return scores.size();
    }

    private UsFinancialQualityScoreRecord scoreRow(
            long securityId,
            UsFinancialStandardizedRecord row,
            UsFinancialDerivedMetricRecord derived,
            Map<PeriodLookupKey, UsFinancialStandardizedRecord> standardizedIndex
    ) {
        BigDecimal revenueGrowth = revenueGrowth(row, standardizedIndex);
        BigDecimal sbcRatio = ratio(row.sbc(), row.revenue());
        BigDecimal equityRatio = ratio(row.equity(), row.totalAssets());
        BigDecimal leverage = derived == null ? null : derived.netDebtToEbitda();
        BigDecimal roic = derived == null ? null : derived.roic();
        BigDecimal accruals = derived == null ? null : derived.accrualsRatio();
        BigDecimal fcfMargin = derived == null ? null : derived.fcfMargin();
        BigDecimal fcfConversion = derived == null ? null : derived.fcfConversion();
        BigDecimal ownerEarnings = derived == null ? null : derived.ownerEarningsEstimate();

        BigDecimal earningsQuality = clamp(score(
                new BigDecimal("0.48"),
                weighted(normalizeBounded(fcfConversion, BigDecimal.ZERO, new BigDecimal("1.20")), new BigDecimal("0.22")),
                weighted(invertDistanceToZero(accruals, new BigDecimal("0.16")), new BigDecimal("0.22")),
                weighted(invertRatio(sbcRatio, new BigDecimal("0.08")), new BigDecimal("0.08"))
        ));

        BigDecimal revenueQuality = clamp(score(
                new BigDecimal("0.42"),
                weighted(normalizeBounded(revenueGrowth, new BigDecimal("-0.10"), new BigDecimal("0.20")), new BigDecimal("0.30")),
                weighted(normalizeBounded(derived == null ? null : derived.grossMargin(), BigDecimal.ZERO, new BigDecimal("0.70")), new BigDecimal("0.18")),
                weighted(normalizeBounded(fcfMargin, new BigDecimal("-0.05"), new BigDecimal("0.30")), new BigDecimal("0.10"))
        ));

        BigDecimal balanceSheet = clamp(score(
                new BigDecimal("0.45"),
                weighted(invertRatio(leverage, new BigDecimal("4.00")), new BigDecimal("0.32")),
                weighted(normalizeBounded(equityRatio, BigDecimal.ZERO, new BigDecimal("0.45")), new BigDecimal("0.18"))
        ));

        BigDecimal capitalEfficiency = clamp(score(
                new BigDecimal("0.35"),
                weighted(normalizeBounded(roic, BigDecimal.ZERO, new BigDecimal("0.30")), new BigDecimal("0.42")),
                weighted(normalizeBounded(derived == null ? null : derived.roa(), BigDecimal.ZERO, new BigDecimal("0.18")), new BigDecimal("0.15"))
        ));

        BigDecimal capitalAllocation = clamp(score(
                new BigDecimal("0.38"),
                weighted(normalizeBounded(fcfMargin, BigDecimal.ZERO, new BigDecimal("0.30")), new BigDecimal("0.20")),
                weighted(normalizeBounded(ownerEarnings == null || row.revenue() == null ? null : ownerEarnings.divide(row.revenue(), 6, RoundingMode.HALF_UP),
                        BigDecimal.ZERO, new BigDecimal("0.30")), new BigDecimal("0.18")),
                weighted(invertRatio(sbcRatio, new BigDecimal("0.08")), new BigDecimal("0.12"))
        ));

        List<BigDecimal> totalInputs = new ArrayList<>();
        totalInputs.add(earningsQuality);
        totalInputs.add(revenueQuality);
        totalInputs.add(balanceSheet);
        totalInputs.add(capitalEfficiency);
        totalInputs.add(capitalAllocation);
        BigDecimal total = average(totalInputs);

        List<String> redFlags = new ArrayList<>();
        List<String> forensicFlags = new ArrayList<>();
        if (roic != null && roic.compareTo(new BigDecimal("0.08")) < 0) {
            redFlags.add("roic_below_cost_of_capital");
        }
        if (accruals != null && accruals.compareTo(new BigDecimal("0.08")) > 0) {
            redFlags.add("accruals_elevated");
            forensicFlags.add("accruals_require_review");
        }
        if (leverage != null && leverage.compareTo(new BigDecimal("3.00")) > 0) {
            redFlags.add("leverage_elevated");
        }
        if (leverage != null && leverage.compareTo(new BigDecimal("4.00")) > 0) {
            forensicFlags.add("net_debt_to_ebitda_above_4x");
        }
        if (revenueGrowth != null && revenueGrowth.compareTo(new BigDecimal("-0.05")) < 0) {
            redFlags.add("revenue_decline");
        }
        if (sbcRatio != null && sbcRatio.compareTo(new BigDecimal("0.05")) > 0) {
            redFlags.add("sbc_ratio_high");
            forensicFlags.add("sbc_dilution_watch");
        }
        if (fcfConversion != null && fcfConversion.compareTo(new BigDecimal("0.70")) < 0) {
            forensicFlags.add("fcf_conversion_weak");
        }

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("roic", roic);
        breakdown.put("accruals_ratio", accruals);
        breakdown.put("net_debt_to_ebitda", leverage);
        breakdown.put("revenue_growth", revenueGrowth);
        breakdown.put("sbc_ratio", sbcRatio);
        breakdown.put("fcf_margin", fcfMargin);
        breakdown.put("fcf_conversion", fcfConversion);
        breakdown.put("equity_ratio", equityRatio);
        breakdown.put("owner_earnings_estimate", ownerEarnings);

        return new UsFinancialQualityScoreRecord(
                securityId,
                row.periodType(),
                row.periodEnd(),
                earningsQuality,
                revenueQuality,
                balanceSheet,
                capitalEfficiency,
                capitalAllocation,
                total,
                toJson(breakdown),
                toJson(redFlags),
                toJson(forensicFlags)
        );
    }

    private boolean shouldScore(UsFinancialStandardizedRecord row, UsFinancialDerivedMetricRecord derived) {
        if (derived == null) {
            return false;
        }
        return derived.roic() != null
                || derived.accrualsRatio() != null
                || derived.netDebtToEbitda() != null
                || derived.fcfMargin() != null
                || row.revenue() != null
                || row.netIncome() != null;
    }

    private BigDecimal revenueGrowth(
            UsFinancialStandardizedRecord row,
            Map<PeriodLookupKey, UsFinancialStandardizedRecord> standardizedIndex
    ) {
        if (row.revenue() == null || row.fiscalYear() == null) {
            return null;
        }
        PeriodLookupKey previousKey = new PeriodLookupKey(
                row.periodType(),
                row.fiscalYear() - 1,
                comparableFiscalPeriod(row)
        );
        UsFinancialStandardizedRecord previous = standardizedIndex.get(previousKey);
        if (previous == null || previous.revenue() == null || previous.revenue().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return row.revenue().subtract(previous.revenue())
                .divide(previous.revenue(), 6, RoundingMode.HALF_UP);
    }

    private String comparableFiscalPeriod(UsFinancialStandardizedRecord row) {
        if ("FY".equals(row.periodType())) {
            return "FY";
        }
        return row.fiscalPeriod();
    }

    private BigDecimal score(BigDecimal base, BigDecimal... contributions) {
        BigDecimal total = base;
        for (BigDecimal contribution : contributions) {
            if (contribution != null) {
                total = total.add(contribution);
            }
        }
        return total;
    }

    private BigDecimal weighted(BigDecimal normalized, BigDecimal weight) {
        return normalized == null ? null : normalized.multiply(weight);
    }

    private BigDecimal normalizeBounded(BigDecimal value, BigDecimal low, BigDecimal high) {
        if (value == null || low == null || high == null || high.compareTo(low) <= 0) {
            return null;
        }
        BigDecimal bounded = value.max(low).min(high);
        return bounded.subtract(low).divide(high.subtract(low), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal invertRatio(BigDecimal value, BigDecimal highWatermark) {
        if (value == null || highWatermark == null || highWatermark.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        if (value.compareTo(highWatermark) >= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.ONE.subtract(value.divide(highWatermark, 6, RoundingMode.HALF_UP));
    }

    private BigDecimal invertDistanceToZero(BigDecimal value, BigDecimal maxDistance) {
        if (value == null || maxDistance == null || maxDistance.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal distance = value.abs();
        if (distance.compareTo(maxDistance) >= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.ONE.subtract(distance.divide(maxDistance, 6, RoundingMode.HALF_UP));
    }

    private BigDecimal average(List<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (BigDecimal value : values) {
            if (value != null) {
                total = total.add(value);
                count += 1;
            }
        }
        if (count == 0) {
            return null;
        }
        return total.divide(BigDecimal.valueOf(count), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal clamp(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.max(new BigDecimal("0.10")).min(new BigDecimal("0.95"));
    }

    private UsFinancialQualityScoreRecord preferRecord(
            UsFinancialQualityScoreRecord left,
            UsFinancialQualityScoreRecord right
    ) {
        int leftCompleteness = completeness(left);
        int rightCompleteness = completeness(right);
        if (rightCompleteness > leftCompleteness) {
            return right;
        }
        if (rightCompleteness < leftCompleteness) {
            return left;
        }

        BigDecimal leftTotal = left.totalQualityScore();
        BigDecimal rightTotal = right.totalQualityScore();
        if (leftTotal == null) {
            return right;
        }
        if (rightTotal == null) {
            return left;
        }
        return rightTotal.compareTo(leftTotal) >= 0 ? right : left;
    }

    private int completeness(UsFinancialQualityScoreRecord record) {
        int populated = 0;
        populated += record.earningsQualityScore() != null ? 1 : 0;
        populated += record.revenueQualityScore() != null ? 1 : 0;
        populated += record.balanceSheetScore() != null ? 1 : 0;
        populated += record.capitalEfficiencyScore() != null ? 1 : 0;
        populated += record.capitalAllocationScore() != null ? 1 : 0;
        populated += record.totalQualityScore() != null ? 1 : 0;
        return populated;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize financial quality payload.", ex);
        }
    }

    private record PeriodKey(
            String periodType,
            Integer fiscalYear,
            String fiscalPeriod,
            LocalDate periodEnd
    ) {
    }

    private record PeriodLookupKey(
            String periodType,
            Integer fiscalYear,
            String fiscalPeriod
    ) {
    }

    private record QualityPeriodKey(
            String periodType,
            LocalDate periodEnd
    ) {
    }
}
