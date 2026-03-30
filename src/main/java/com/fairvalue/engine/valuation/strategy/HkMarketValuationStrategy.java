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
public class HkMarketValuationStrategy implements MarketValuationStrategy {
    @Override
    public Market market() {
        return Market.HK;
    }

    @Override
    public MarketComputation evaluate(StockSnapshot snapshot) {
        StockFundamentals f = snapshot.fundamentals();
        double price = snapshot.price();

        double dividendValue = price * MathSupport.clamp(f.dividendYield() / 0.045, 0.65, 1.55);
        double navValue = price * MathSupport.clamp(1.0 / Math.max(f.pb(), 0.55), 0.70, 1.55);

        double ahParityAnchor = MathSupport.clamp(1.0 + f.ahPremiumGap() * 0.50 - (1.0 - f.liquidityScore()) * 0.25, 0.70, 1.40);
        double ahParityValue = price * ahParityAnchor;

        List<ModelValuation> models = List.of(
                new ModelValuation("dividend_valuation", MathSupport.round(dividendValue), 0.40, "Dividend yield is a primary anchor in discount markets."),
                new ModelValuation("nav_anchor", MathSupport.round(navValue), 0.35, "NAV/PB anchors valuation for asset-heavy HK listings."),
                new ModelValuation("ah_parity", MathSupport.round(ahParityValue), 0.25, "A/H premium gap indicates potential convergence or persistent discount.")
        );

        double liquidityDiscount = -MathSupport.clamp((0.65 - f.liquidityScore()) * 0.14, 0.0, 0.08);
        double usdRateDiscount = -MathSupport.clamp(f.chinaExposure() * 0.01 + 0.01, 0.01, 0.03);
        double buybackSupport = MathSupport.clamp(f.buybackYield() * 0.45, 0.0, 0.03);

        List<MarketAdjustment> adjustments = List.of(
                new MarketAdjustment("liquidity_discount", MathSupport.round(liquidityDiscount), "Liquidity segmentation often keeps HK valuation below theoretical value."),
                new MarketAdjustment("usd_rate_environment", MathSupport.round(usdRateDiscount), "USD/HKD rate environment can pressure equity multiples."),
                new MarketAdjustment("buyback_support", MathSupport.round(buybackSupport), "Consistent buybacks partially offset discount persistence.")
        );

        List<String> riskFlags = new ArrayList<>();
        if (f.liquidityScore() < 0.35) {
            riskFlags.add("low_liquidity_discount");
        }
        if (f.ahPremiumGap() < -0.18) {
            riskFlags.add("ah_discount_widening");
        }
        if (f.chinaExposure() > 0.70) {
            riskFlags.add("high_china_macro_exposure");
        }

        Map<String, Double> drivers = new LinkedHashMap<>();
        drivers.put("dividend_yield", f.dividendYield());
        drivers.put("pb", f.pb());
        drivers.put("liquidity_score", f.liquidityScore());
        drivers.put("ah_premium_gap", f.ahPremiumGap());
        drivers.put("china_exposure", f.chinaExposure());

        double confidenceBase = MathSupport.clamp(0.54 + f.dataCompleteness() * 0.11 + f.liquidityScore() * 0.10, 0.35, 0.83);

        return new MarketComputation(
                models,
                adjustments,
                riskFlags,
                drivers,
                confidenceBase,
                "HK market values tradability adjustments with dividend and discount framework.",
                "Selected dividend + NAV + A/H parity because structural discount and liquidity are first-order drivers."
        );
    }
}
