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
public class UsMarketValuationStrategy implements MarketValuationStrategy {
    @Override
    public Market market() {
        return Market.US;
    }

    @Override
    public MarketComputation evaluate(StockSnapshot snapshot) {
        StockFundamentals f = snapshot.fundamentals();
        double price = snapshot.price();

        double dcfFactor = MathSupport.clamp(
                1.0 + f.revenueGrowth() * 1.8 + f.fcfMargin() * 1.4 - f.wacc() + f.terminalGrowth() * 2.0 - f.sbcRatio() * 0.5 + f.buybackYield() * 0.8,
                0.65,
                1.70
        );
        double dcfValue = price * dcfFactor;

        double evEbitdaAnchor = MathSupport.clamp(14.0 / Math.max(f.evEbitda(), 4.0), 0.70, 1.35);
        double evEbitdaValue = price * evEbitdaAnchor;

        double peg = Math.max((f.pe() / Math.max(f.revenueGrowth() * 100.0, 4.0)), 0.25);
        double pegAnchor = MathSupport.clamp(1.25 / peg, 0.60, 1.45);
        double pegValue = price * pegAnchor;

        List<ModelValuation> models = List.of(
                new ModelValuation("dcf", MathSupport.round(dcfValue), 0.45, "Cash-flow and growth expectations dominate large US equities."),
                new ModelValuation("ev_ebitda", MathSupport.round(evEbitdaValue), 0.30, "Relative valuation calibrates market multiple to operating earnings."),
                new ModelValuation("peg", MathSupport.round(pegValue), 0.25, "PEG keeps valuation anchored to growth-adjusted earnings multiples.")
        );

        double growthPremium = MathSupport.clamp(f.analystCoverage() * 0.02, 0.0, 0.03);
        double expectationDiscount = -MathSupport.clamp(Math.max(f.pe() - 28.0, 0.0) / 300.0, 0.0, 0.04);
        double dilutionDiscount = -MathSupport.clamp(f.sbcRatio() * 0.45, 0.0, 0.03);

        List<MarketAdjustment> adjustments = List.of(
                new MarketAdjustment("growth_premium", MathSupport.round(growthPremium), "Strong analyst coverage tends to support growth rerating."),
                new MarketAdjustment("expectation_discount", MathSupport.round(expectationDiscount), "High embedded expectations raise de-rating risk."),
                new MarketAdjustment("sbc_dilution", MathSupport.round(dilutionDiscount), "Stock-based compensation dilutes per-share fair value.")
        );

        List<String> riskFlags = new ArrayList<>();
        if (f.pe() > 30.0) {
            riskFlags.add("high_expectation_embedded");
        }
        if (f.sbcRatio() > 0.08) {
            riskFlags.add("high_sbc_dilution");
        }
        if (f.earningsVolatility() > 0.42) {
            riskFlags.add("earnings_volatility");
        }

        Map<String, Double> drivers = new LinkedHashMap<>();
        drivers.put("revenue_growth", f.revenueGrowth());
        drivers.put("fcf_margin", f.fcfMargin());
        drivers.put("wacc", f.wacc());
        drivers.put("terminal_growth", f.terminalGrowth());
        drivers.put("buyback_yield", f.buybackYield());

        double confidenceBase = MathSupport.clamp(0.74 + f.dataCompleteness() * 0.10 + f.analystCoverage() * 0.05, 0.60, 0.92);

        return new MarketComputation(
                models,
                adjustments,
                riskFlags,
                drivers,
                confidenceBase,
                "US market prioritizes cash-flow and growth discounting with expectation calibration.",
                "Selected DCF + EV/EBITDA + PEG because US coverage quality is high and growth expectations are quickly priced in."
        );
    }
}
