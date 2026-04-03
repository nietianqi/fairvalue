package com.fairvalue.engine.valuation.strategy;

import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.domain.StockFundamentals;
import com.fairvalue.engine.domain.StockSnapshot;
import com.fairvalue.engine.valuation.MarketAdjustment;
import com.fairvalue.engine.valuation.MarketComputation;
import com.fairvalue.engine.valuation.ModelValuation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CnMarketValuationStrategy implements MarketValuationStrategy {
    @Override
    public Market market() {
        return Market.CN;
    }

    @Override
    public MarketComputation evaluate(StockSnapshot snapshot) {
        StockFundamentals f = snapshot.fundamentals();
        double price = snapshot.price();

        double relativeAnchor = MathSupport.clamp((18.0 + f.roe() * 35.0) / Math.max(f.pe(), 6.0), 0.65, 1.50);
        double relativeValue = price * relativeAnchor;

        double peg = Math.max((f.pe() / Math.max(f.revenueGrowth() * 100.0, 5.0)), 0.30);
        double pegValue = price * MathSupport.clamp(1.10 / peg, 0.60, 1.40);

        double dcfValue = price * MathSupport.clamp(1.0 + f.fcfMargin() * 1.1 + f.revenueGrowth() * 0.7 - f.wacc() + f.terminalGrowth() * 1.8, 0.60, 1.50);
        double midCycleValue = price * MathSupport.clamp(1.0 + f.roe() * 0.45 - f.earningsVolatility() * 0.35, 0.60, 1.35);
        double navValue = price * MathSupport.clamp(1.0 + Math.max(f.netCashToMarketCap(), -0.10) * 0.7 + (1.0 / Math.max(f.pb(), 0.9) - 0.8) * 0.30, 0.60, 1.55);

        List<ModelValuation> models = List.of(
                new ModelValuation("relative_pe_pb", MathSupport.round(relativeValue), 0.30, "A-share pricing still relies heavily on relative valuation anchors."),
                new ModelValuation("peg", MathSupport.round(pegValue), 0.20, "PEG balances growth potential and earnings multiple risk."),
                new ModelValuation("dcf", MathSupport.round(dcfValue), 0.20, "DCF provides absolute-value anchor under normalized cash-flow assumptions."),
                new ModelValuation("mid_cycle_profit", MathSupport.round(midCycleValue), 0.15, "Mid-cycle valuation avoids peak-earnings overestimation for cyclical names."),
                new ModelValuation("nav_asset", MathSupport.round(navValue), 0.15, "NAV/PB anchor reflects asset backing and balance-sheet resilience.")
        );

        double policyFactor = MathSupport.clamp(1.0 - f.policySensitivity() * 0.20 + f.themePremium() * 0.06, 0.60, 1.10);
        double liquidityFactor = MathSupport.clamp(0.85 + f.liquidityScore() * 0.18, 0.70, 1.02);
        double governanceFactor = MathSupport.clamp(0.82 + f.governanceScore() * 0.21, 0.60, 1.03);
        double structureFactor = MathSupport.clamp(1.0 - Math.max(0.0, f.sbcRatio() - 0.02) * 1.5, 0.70, 1.00);
        double styleFactor = MathSupport.clamp(0.95 + f.themePremium() * 0.12, 0.90, 1.08);

        List<MarketAdjustment> adjustments = List.of(
                new MarketAdjustment("policy_factor", MathSupport.round(policyFactor - 1.0), "Policy environment impacts valuation rerating speed and risk premium."),
                new MarketAdjustment("liquidity_factor", MathSupport.round(liquidityFactor - 1.0), "Liquidity segmentation causes tradable-value discounts."),
                new MarketAdjustment("governance_factor", MathSupport.round(governanceFactor - 1.0), "Governance quality directly affects valuation sustainability."),
                new MarketAdjustment("structure_factor", MathSupport.round(structureFactor - 1.0), "Potential dilution and structure risk pressure fair value."),
                new MarketAdjustment("style_factor", MathSupport.round(styleFactor - 1.0), "Market style rotation influences short-to-mid term pricing band.")
        );

        List<String> riskFlags = new ArrayList<>();
        if (f.policySensitivity() > 0.55) {
            riskFlags.add("policy_sensitive");
        }
        if (f.themePremium() > 0.65) {
            riskFlags.add("high_theme_premium");
        }
        if (!f.positiveFreeCashFlow()) {
            riskFlags.add("negative_free_cash_flow");
            riskFlags.add("potential_value_trap");
        }
        if (f.sbcRatio() > 0.06) {
            riskFlags.add("dilution_pressure");
        }

        Map<String, Double> drivers = new LinkedHashMap<>();
        drivers.put("revenue_growth", f.revenueGrowth());
        drivers.put("roe", f.roe());
        drivers.put("policy_sensitivity", f.policySensitivity());
        drivers.put("theme_premium", f.themePremium());
        drivers.put("liquidity_score", f.liquidityScore());
        drivers.put("governance_score", f.governanceScore());

        double confidenceBase = MathSupport.clamp(0.56 + f.dataCompleteness() * 0.12 + (f.positiveFreeCashFlow() ? 0.06 : -0.06), 0.35, 0.84);

        return new MarketComputation(
                models,
                adjustments,
                riskFlags,
                drivers,
                confidenceBase,
                "CN market uses relative + absolute + asset valuation with policy/liquidity/governance/style corrections.",
                "Selected PE/PB anchor + PEG + DCF + Mid-cycle + NAV to satisfy multi-model cross validation for China equities."
        );
    }
}
