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

        double pbRoeKeAnchor = MathSupport.clamp((f.roe() * 100.0) / Math.max((f.pb() * 10.5) + (f.wacc() * 20.0), 4.0), 0.60, 1.55);
        double pbRoeKeValue = price * pbRoeKeAnchor;

        double evEbitdaAnchor = MathSupport.clamp(8.5 / Math.max(f.evEbitda(), 3.5), 0.65, 1.45);
        double evEbitdaValue = price * evEbitdaAnchor;

        double dcfAnchor = MathSupport.clamp(1.0 + f.fcfMargin() * 1.0 + f.revenueGrowth() * 0.70 - f.wacc() + f.terminalGrowth() * 1.9, 0.70, 1.45);
        double dcfValue = price * dcfAnchor;

        double dividendAnchor = MathSupport.clamp(f.dividendYield() / 0.024 + f.buybackYield() * 3.5, 0.70, 1.45);
        double ddmValue = price * dividendAnchor;

        double navAnchor = MathSupport.clamp(1.0 + f.netCashToMarketCap() * 0.85 + Math.max(0.0, (1.0 / Math.max(f.pb(), 0.7)) - 0.9) * 0.30, 0.70, 1.50);
        double navValue = price * navAnchor;

        List<ModelValuation> models = List.of(
                new ModelValuation("pb_roe_ke", MathSupport.round(pbRoeKeValue), 0.30, "PBR x ROE framework remains core under TSE capital efficiency reforms."),
                new ModelValuation("ev_ebitda", MathSupport.round(evEbitdaValue), 0.20, "EV/EBITDA captures normalized operating earnings across industrial sectors."),
                new ModelValuation("dcf", MathSupport.round(dcfValue), 0.20, "DCF anchors long-term cash generation and discount-rate assumptions."),
                new ModelValuation("ddm_shareholder_return", MathSupport.round(ddmValue), 0.15, "Dividend + buyback yield reflects shareholder return policy changes."),
                new ModelValuation("nav_sotp", MathSupport.round(navValue), 0.15, "Net-cash and hidden-asset optionality are meaningful in Japan equities.")
        );

        double governancePremium = MathSupport.clamp((f.governanceScore() - 0.50) * 0.09, -0.03, 0.05);
        double buybackPremium = MathSupport.clamp(f.buybackYield() * 0.70, 0.0, 0.04);
        double lowLiquidityDiscount = -MathSupport.clamp((0.55 - f.liquidityScore()) * 0.10, 0.0, 0.05);
        double fxSensitivityDiscount = -MathSupport.clamp((f.earningsVolatility() - 0.18) * 0.08, 0.0, 0.03);
        double crossShareholdingDiscount = -MathSupport.clamp((0.45 - f.governanceScore()) * 0.12, 0.0, 0.04);

        List<MarketAdjustment> adjustments = List.of(
                new MarketAdjustment("governance_reform", MathSupport.round(governancePremium), "Corporate governance and capital policy reforms support rerating."),
                new MarketAdjustment("buyback_support", MathSupport.round(buybackPremium), "Stable buybacks improve per-share value realization."),
                new MarketAdjustment("liquidity_discount", MathSupport.round(lowLiquidityDiscount), "Thin liquidity delays convergence to fair value."),
                new MarketAdjustment("fx_sensitivity", MathSupport.round(fxSensitivityDiscount), "FX assumption mismatch adds uncertainty to earnings visibility."),
                new MarketAdjustment("cross_shareholding_discount", MathSupport.round(crossShareholdingDiscount), "Low governance transparency may imply persistent holding-company discount.")
        );

        List<String> riskFlags = new ArrayList<>();
        if (f.pb() < 0.85 && f.governanceScore() < 0.50) {
            riskFlags.add("value_trap_risk");
        }
        if (f.liquidityScore() < 0.35) {
            riskFlags.add("thin_liquidity");
        }
        if (f.earningsVolatility() > 0.32) {
            riskFlags.add("fx_earnings_sensitivity");
        }
        if (f.buybackYield() < 0.003 && f.dividendYield() < 0.015) {
            riskFlags.add("weak_shareholder_return_policy");
        }

        Map<String, Double> drivers = new LinkedHashMap<>();
        drivers.put("roe", f.roe());
        drivers.put("pb", f.pb());
        drivers.put("net_cash_to_market_cap", f.netCashToMarketCap());
        drivers.put("dividend_yield", f.dividendYield());
        drivers.put("buyback_yield", f.buybackYield());
        drivers.put("governance_score", f.governanceScore());
        drivers.put("wacc", f.wacc());

        double confidenceBase = MathSupport.clamp(0.68 + f.dataCompleteness() * 0.10 + f.governanceScore() * 0.08 + f.liquidityScore() * 0.06, 0.50, 0.92);

        return new MarketComputation(
                models,
                adjustments,
                riskFlags,
                drivers,
                confidenceBase,
                "JP valuation combines PB-ROE-KE, normalized EV/EBITDA, DCF and shareholder-return anchors.",
                "Selected 5-method cross validation to reflect Japanese market specifics: governance reform, net cash, capital return, and structural discounts."
        );
    }
}
