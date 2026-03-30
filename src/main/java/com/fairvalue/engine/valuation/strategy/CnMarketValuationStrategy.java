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

        double peerPe = 18.0 + f.themePremium() * 12.0;
        double relativeAnchor = MathSupport.clamp(peerPe / Math.max(f.pe(), 5.0), 0.65, 1.45);
        double relativeValue = price * relativeAnchor;

        double peg = Math.max((f.pe() / Math.max(f.revenueGrowth() * 100.0, 5.0)), 0.30);
        double pegValue = price * MathSupport.clamp(1.10 / peg, 0.60, 1.35);

        double qualityAnchor = MathSupport.clamp(1.0 + f.roe() * 0.7 - f.policySensitivity() * 0.5 + (f.positiveFreeCashFlow() ? 0.06 : -0.08), 0.60, 1.30);
        double qualityValue = price * qualityAnchor;

        List<ModelValuation> models = List.of(
                new ModelValuation("relative_multiple", MathSupport.round(relativeValue), 0.45, "Relative PE/PB remains the dominant pricing anchor in A-share rotation cycles."),
                new ModelValuation("peg", MathSupport.round(pegValue), 0.30, "PEG balances valuation against growth and mitigates one-period earnings noise."),
                new ModelValuation("quality_anchor", MathSupport.round(qualityValue), 0.25, "Quality anchor discounts policy-sensitive low-quality earnings.")
        );

        double policyDiscount = -MathSupport.clamp(f.policySensitivity() * 0.12, 0.01, 0.06);
        double sentimentPremium = MathSupport.clamp(f.themePremium() * 0.08, -0.02, 0.05);
        double liquidityAdjustment = MathSupport.clamp((f.liquidityScore() - 0.5) * 0.06, -0.03, 0.03);

        List<MarketAdjustment> adjustments = List.of(
                new MarketAdjustment("policy_discount", MathSupport.round(policyDiscount), "Policy uncertainty can compress valuation windows quickly."),
                new MarketAdjustment("theme_sentiment", MathSupport.round(sentimentPremium), "Theme premium captures style rotation and retail sentiment."),
                new MarketAdjustment("liquidity_adjustment", MathSupport.round(liquidityAdjustment), "Higher turnover supports tradable fair value realization.")
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
        }

        Map<String, Double> drivers = new LinkedHashMap<>();
        drivers.put("revenue_growth", f.revenueGrowth());
        drivers.put("roe", f.roe());
        drivers.put("policy_sensitivity", f.policySensitivity());
        drivers.put("theme_premium", f.themePremium());
        drivers.put("liquidity_score", f.liquidityScore());

        double confidenceBase = MathSupport.clamp(0.58 + f.dataCompleteness() * 0.12 + (f.positiveFreeCashFlow() ? 0.05 : -0.05), 0.35, 0.82);

        return new MarketComputation(
                models,
                adjustments,
                riskFlags,
                drivers,
                confidenceBase,
                "CN market emphasizes relative valuation, earnings quality, and policy/sentiment adjustments.",
                "Selected relative multiple + PEG + quality anchor to reflect fast style rotation and policy-driven repricing."
        );
    }
}
