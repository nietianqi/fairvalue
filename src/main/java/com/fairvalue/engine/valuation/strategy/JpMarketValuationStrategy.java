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
public class JpMarketValuationStrategy implements MarketValuationStrategy {
    @Override
    public Market market() {
        return Market.JP;
    }

    @Override
    public MarketComputation evaluate(StockSnapshot snapshot) {
        StockFundamentals f = snapshot.fundamentals();
        double price = snapshot.price();

        double pbRoeAnchor = MathSupport.clamp((f.roe() * 100.0) / Math.max(f.pb() * 11.0, 4.0), 0.65, 1.50);
        double pbRoeValue = price * pbRoeAnchor;

        double netCashValue = price * MathSupport.clamp(1.0 + f.netCashToMarketCap() * 0.75, 0.75, 1.45);
        double dividendValue = price * MathSupport.clamp(f.dividendYield() / 0.022, 0.70, 1.45);

        List<ModelValuation> models = List.of(
                new ModelValuation("pb_roe_framework", MathSupport.round(pbRoeValue), 0.40, "PB-ROE framework captures valuation repair potential under governance reform."),
                new ModelValuation("net_cash_adjusted", MathSupport.round(netCashValue), 0.35, "Large net cash positions justify upward adjustment to equity value."),
                new ModelValuation("dividend_anchor", MathSupport.round(dividendValue), 0.25, "Dividend valuation anchors fair value in low-growth sectors.")
        );

        double governancePremium = MathSupport.clamp((f.governanceScore() - 0.50) * 0.08, -0.02, 0.04);
        double buybackPremium = MathSupport.clamp(f.buybackYield() * 0.55, 0.0, 0.03);
        double lowLiquidityDiscount = -MathSupport.clamp((0.5 - f.liquidityScore()) * 0.10, 0.0, 0.04);

        List<MarketAdjustment> adjustments = List.of(
                new MarketAdjustment("governance_reform", MathSupport.round(governancePremium), "Improving governance and capital efficiency support rerating."),
                new MarketAdjustment("buyback_support", MathSupport.round(buybackPremium), "Stable buybacks increase per-share value realization."),
                new MarketAdjustment("liquidity_discount", MathSupport.round(lowLiquidityDiscount), "Thin trading can delay fair value convergence.")
        );

        List<String> riskFlags = new ArrayList<>();
        if (f.pb() < 0.8 && f.governanceScore() < 0.45) {
            riskFlags.add("value_trap_risk");
        }
        if (f.liquidityScore() < 0.35) {
            riskFlags.add("thin_liquidity");
        }

        Map<String, Double> drivers = new LinkedHashMap<>();
        drivers.put("roe", f.roe());
        drivers.put("net_cash_to_market_cap", f.netCashToMarketCap());
        drivers.put("dividend_yield", f.dividendYield());
        drivers.put("buyback_yield", f.buybackYield());
        drivers.put("governance_score", f.governanceScore());

        double confidenceBase = MathSupport.clamp(0.66 + f.dataCompleteness() * 0.12 + f.governanceScore() * 0.08, 0.52, 0.90);

        return new MarketComputation(
                models,
                adjustments,
                riskFlags,
                drivers,
                confidenceBase,
                "JP market prioritizes asset quality, net cash, and shareholder return improvement.",
                "Selected PB-ROE + net cash adjusted + dividend anchor for structurally low-PB Japanese equities."
        );
    }
}
