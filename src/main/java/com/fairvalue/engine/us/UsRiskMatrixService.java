package com.fairvalue.engine.us;

import com.fairvalue.engine.api.dto.us.UsRiskItem;
import com.fairvalue.engine.domain.StockFundamentals;
import com.fairvalue.engine.valuation.strategy.MathSupport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UsRiskMatrixService {
    public UsRiskMatrixResult evaluate(UsValuationModelContext context, double baselineWacc) {
        StockFundamentals fundamentals = context.snapshot().fundamentals();
        List<UsRiskItem> items = new ArrayList<>();

        addFcffStructureRisk(items, context);
        addDataQualityRisk(items, context);
        addLeverageRisk(items, context, fundamentals);
        addDilutionRisk(items, context, fundamentals);
        addGovernanceRisk(items, context, fundamentals);
        addMultipleCompressionRisk(items, fundamentals);
        addExecutionRisk(items, context, fundamentals);
        addValueTrapRisk(items, context, fundamentals, baselineWacc);

        double waccAdjustment = MathSupport.round(items.stream()
                .filter(item -> "wacc".equals(item.adjustmentType()))
                .mapToDouble(UsRiskItem::adjustment)
                .sum());
        double scenarioPressure = items.stream()
                .filter(item -> "scenario_weight".equals(item.adjustmentType()))
                .mapToDouble(UsRiskItem::adjustment)
                .sum();
        double marginOfSafetyAdjustment = MathSupport.round(items.stream()
                .filter(item -> "margin_of_safety".equals(item.adjustmentType()))
                .mapToDouble(UsRiskItem::adjustment)
                .sum());
        double confidencePenalty = MathSupport.round(items.stream()
                .mapToDouble(item -> switch (item.impact()) {
                    case "High" -> 0.015;
                    case "Medium" -> 0.008;
                    default -> 0.003;
                })
                .sum());
        boolean valueTrapFlag = items.stream().anyMatch(item -> "value_trap".equals(item.riskType()));

        double bearProbabilityDelta = MathSupport.round(MathSupport.clamp(
                scenarioPressure + valueTrapFlagAdjustment(valueTrapFlag, 0.03),
                0.0,
                0.14
        ));
        double bullProbabilityDelta = MathSupport.round(-MathSupport.clamp(
                scenarioPressure * 0.75 + valueTrapFlagAdjustment(valueTrapFlag, 0.02),
                0.0,
                0.10
        ));

        return new UsRiskMatrixResult(
                items,
                MathSupport.clamp(waccAdjustment, 0.0, 0.03),
                bearProbabilityDelta,
                bullProbabilityDelta,
                MathSupport.clamp(marginOfSafetyAdjustment, 0.0, 0.12),
                MathSupport.clamp(confidencePenalty, 0.0, 0.18),
                valueTrapFlag
        );
    }

    private void addFcffStructureRisk(List<UsRiskItem> items, UsValuationModelContext context) {
        boolean hasRevenue = context.financials().stream().anyMatch(record -> positive(record.revenue()));
        boolean hasShares = context.financials().stream().anyMatch(record -> positive(record.dilutedShares()) || positive(record.basicShares()));
        if (!hasRevenue && context.secProfile() != null && context.secProfile().annualRevenue() != null && context.secProfile().annualRevenue() > 0.0) {
            hasRevenue = true;
        }
        if (!hasShares && context.secProfile() != null && context.secProfile().sharesOutstanding() != null && context.secProfile().sharesOutstanding() > 0.0) {
            hasShares = true;
        }
        if (hasRevenue && hasShares) {
            return;
        }
        items.add(new UsRiskItem(
                "fcff_structure_gap",
                "Medium",
                "Medium",
                "margin_of_safety",
                0.03,
                "FCFF still relies on fallback anchors because structured revenue or share-count inputs are incomplete."
        ));
    }

    private void addDataQualityRisk(List<UsRiskItem> items, UsValuationModelContext context) {
        double confidence = context.latestAudit() != null && context.latestAudit().confidenceLevel() != null
                ? context.latestAudit().confidenceLevel().doubleValue()
                : 0.62;
        if (confidence >= 0.82) {
            return;
        }
        items.add(new UsRiskItem(
                "data_quality_gap",
                labelDescending(confidence, 0.60, 0.74),
                labelDescending(confidence, 0.58, 0.72),
                "wacc",
                MathSupport.round(MathSupport.clamp((0.82 - confidence) * 0.03, 0.004, 0.012)),
                "Lower data confidence raises discount-rate discipline and reduces willingness to pay for distant cash flows."
        ));
    }

    private void addLeverageRisk(List<UsRiskItem> items, UsValuationModelContext context, StockFundamentals fundamentals) {
        double leverage = context.latestDerived() != null && context.latestDerived().netDebtToEbitda() != null
                ? context.latestDerived().netDebtToEbitda().doubleValue()
                : fundamentals.netCashToMarketCap() < 0.0 ? Math.abs(fundamentals.netCashToMarketCap()) * 12.0 : 0.0;
        if (leverage < 1.8) {
            return;
        }
        items.add(new UsRiskItem(
                "leverage",
                labelAscending(leverage, 3.0, 2.0),
                labelAscending(leverage, 3.5, 2.3),
                "wacc",
                MathSupport.round(MathSupport.clamp((leverage - 1.8) * 0.0035, 0.003, 0.015)),
                "Balance-sheet leverage can tighten refinancing flexibility and amplify downside in weaker demand regimes."
        ));
    }

    private void addDilutionRisk(List<UsRiskItem> items, UsValuationModelContext context, StockFundamentals fundamentals) {
        double sbcRatio = fundamentals.sbcRatio();
        if (sbcRatio < 0.035) {
            return;
        }
        items.add(new UsRiskItem(
                "dilution",
                labelAscending(sbcRatio, 0.08, 0.05),
                labelAscending(sbcRatio, 0.09, 0.055),
                "margin_of_safety",
                MathSupport.round(MathSupport.clamp(sbcRatio * 0.35, 0.012, 0.045)),
                "Elevated SBC dilution reduces per-share capture of intrinsic value and warrants a wider buy discount."
        ));
    }

    private void addGovernanceRisk(List<UsRiskItem> items, UsValuationModelContext context, StockFundamentals fundamentals) {
        double governance = fundamentals.governanceScore();
        double quality = context.latestQualityScore() != null && context.latestQualityScore().totalQualityScore() != null
                ? context.latestQualityScore().totalQualityScore().doubleValue()
                : 0.72;
        if (governance >= 0.62 && quality >= 0.68) {
            return;
        }
        items.add(new UsRiskItem(
                "governance_execution",
                labelDescending(Math.min(governance, quality), 0.48, 0.62),
                labelDescending(Math.min(governance, quality), 0.46, 0.60),
                "margin_of_safety",
                MathSupport.round(MathSupport.clamp((0.70 - Math.min(governance, quality)) * 0.08, 0.01, 0.05)),
                "Weaker governance or operating quality increases the chance that modeled cash-flow durability does not fully materialize."
        ));
    }

    private void addMultipleCompressionRisk(List<UsRiskItem> items, StockFundamentals fundamentals) {
        if (fundamentals.pe() < 24.0) {
            return;
        }
        items.add(new UsRiskItem(
                "multiple_compression",
                labelAscending(fundamentals.pe(), 34.0, 26.0),
                labelAscending(fundamentals.pe(), 38.0, 28.0),
                "scenario_weight",
                MathSupport.round(MathSupport.clamp((fundamentals.pe() - 24.0) / 240.0, 0.015, 0.07)),
                "Premium valuation leaves less room for execution misses and pushes scenario weight toward bear/base outcomes."
        ));
    }

    private void addExecutionRisk(List<UsRiskItem> items, UsValuationModelContext context, StockFundamentals fundamentals) {
        double earningsVolatility = fundamentals.earningsVolatility();
        boolean recent8k = context.latestAudit() != null && context.latestAudit().hasRecent8k();
        if (earningsVolatility < 0.24 && !recent8k) {
            return;
        }
        items.add(new UsRiskItem(
                "execution_variance",
                recent8k ? "High" : labelAscending(earningsVolatility, 0.36, 0.25),
                recent8k ? "Medium" : labelAscending(earningsVolatility, 0.40, 0.28),
                "scenario_weight",
                MathSupport.round(MathSupport.clamp(earningsVolatility * 0.10 + (recent8k ? 0.02 : 0.0), 0.012, 0.06)),
                recent8k
                        ? "Recent 8-K activity suggests the base case should leave more room for event-driven volatility."
                        : "Higher earnings variance widens the range of near-term operating outcomes."
        ));
    }

    private void addValueTrapRisk(
            List<UsRiskItem> items,
            UsValuationModelContext context,
            StockFundamentals fundamentals,
            double baselineWacc
    ) {
        double roic = context.latestDerived() != null && context.latestDerived().roic() != null
                ? context.latestDerived().roic().doubleValue()
                : fundamentals.roe() * 0.65;
        double fcfMargin = context.latestDerived() != null && context.latestDerived().fcfMargin() != null
                ? context.latestDerived().fcfMargin().doubleValue()
                : fundamentals.fcfMargin();
        double quality = context.latestQualityScore() != null && context.latestQualityScore().totalQualityScore() != null
                ? context.latestQualityScore().totalQualityScore().doubleValue()
                : 0.70;

        boolean valueTrap = roic < baselineWacc && fcfMargin < 0.10 && quality < 0.62;
        if (!valueTrap) {
            return;
        }
        items.add(new UsRiskItem(
                "value_trap",
                "High",
                "High",
                "margin_of_safety",
                0.06,
                "Returns on capital trail the cost of capital while cash conversion stays thin, so the stock needs a larger required discount."
        ));
    }

    private double valueTrapFlagAdjustment(boolean valueTrapFlag, double adjustment) {
        return valueTrapFlag ? adjustment : 0.0;
    }

    private String labelAscending(double value, double high, double medium) {
        if (value >= high) {
            return "High";
        }
        if (value >= medium) {
            return "Medium";
        }
        return "Low";
    }

    private String labelDescending(double value, double highRisk, double mediumRisk) {
        if (value <= highRisk) {
            return "High";
        }
        if (value <= mediumRisk) {
            return "Medium";
        }
        return "Low";
    }

    private boolean positive(java.math.BigDecimal value) {
        return value != null && value.doubleValue() > 0.0;
    }
}
