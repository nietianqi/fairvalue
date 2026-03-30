package com.fairvalue.engine.service;

import com.fairvalue.engine.api.dto.ScenarioRequest;
import com.fairvalue.engine.api.dto.ScreenerRequest;
import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.domain.StockFundamentals;
import com.fairvalue.engine.domain.StockSnapshot;
import com.fairvalue.engine.domain.ValuationStatus;
import com.fairvalue.engine.valuation.ExplainResult;
import com.fairvalue.engine.valuation.HistoryPoint;
import com.fairvalue.engine.valuation.MarketAdjustment;
import com.fairvalue.engine.valuation.MarketComputation;
import com.fairvalue.engine.valuation.ModelValuation;
import com.fairvalue.engine.valuation.ScenarioPoint;
import com.fairvalue.engine.valuation.ScenarioResult;
import com.fairvalue.engine.valuation.ValuationResult;
import com.fairvalue.engine.valuation.strategy.MarketStrategyRegistry;
import com.fairvalue.engine.valuation.strategy.MathSupport;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ValuationService {
    private final MarketDataService marketDataService;
    private final MarketStrategyRegistry strategyRegistry;

    public ValuationService(MarketDataService marketDataService, MarketStrategyRegistry strategyRegistry) {
        this.marketDataService = marketDataService;
        this.strategyRegistry = strategyRegistry;
    }

    public ValuationResult valuate(Market market, String symbol) {
        return runValuation(market, symbol).result();
    }

    public List<ValuationResult> batchValuate(List<MarketSymbol> items) {
        return items.stream()
                .map(item -> valuate(item.market(), item.symbol()))
                .toList();
    }

    public ExplainResult explain(Market market, String symbol) {
        EngineOutput output = runValuation(market, symbol);
        MarketComputation computation = output.computation();
        Map<String, Double> topDrivers = new LinkedHashMap<>();

        computation.drivers().entrySet().stream()
                .sorted((a, b) -> Double.compare(Math.abs(b.getValue()), Math.abs(a.getValue())))
                .limit(6)
                .forEach(entry -> topDrivers.put(entry.getKey(), MathSupport.round(entry.getValue())));

        return new ExplainResult(
                market.name(),
                output.result().symbol(),
                computation.methodology(),
                computation.modelSelectionReason(),
                topDrivers,
                output.confidenceBreakdown(),
                output.result().models(),
                output.result().marketAdjustments(),
                output.result().riskFlags()
        );
    }

    public ScenarioResult scenario(ScenarioRequest request) {
        Market market = Market.from(request.market());
        EngineOutput output = runValuation(market, request.symbol());
        StockFundamentals f = output.snapshot().fundamentals();
        double base = output.result().tradableFairValue();
        double price = output.result().price();

        double growthEffect = request.revenueGrowth() == null ? 0.0 : (request.revenueGrowth() - f.revenueGrowth()) * 0.90;
        double marginEffect = request.ebitMargin() == null ? 0.0 : (request.ebitMargin() - f.fcfMargin()) * 0.45;
        double waccEffect = request.wacc() == null ? 0.0 : (f.wacc() - request.wacc()) * 2.1;
        double terminalEffect = request.terminalGrowth() == null ? 0.0 : (request.terminalGrowth() - f.terminalGrowth()) * 1.5;
        double peEffect = request.targetPe() == null ? 0.0 : ((request.targetPe() / Math.max(f.pe(), 3.0)) - 1.0) * 0.45;
        double pbEffect = request.targetPb() == null ? 0.0 : ((request.targetPb() / Math.max(f.pb(), 0.4)) - 1.0) * 0.30;

        double scenarioFactor = MathSupport.clamp(1.0 + growthEffect + marginEffect + waccEffect + terminalEffect + peEffect + pbEffect, 0.60, 1.55);
        double mid = base * scenarioFactor;
        double pessimistic = mid * 0.88;
        double optimistic = mid * 1.12;

        double relativeBand = (output.result().fairValueHigh() - output.result().fairValueLow()) / (2.0 * base);
        double band = MathSupport.clamp(relativeBand, 0.08, 0.28);

        List<ScenarioPoint> scenarios = List.of(
                toScenario("pessimistic", pessimistic, band, price),
                toScenario("neutral", mid, band, price),
                toScenario("optimistic", optimistic, band, price)
        );

        Map<String, Double> sensitivity = new LinkedHashMap<>();
        sensitivity.put("revenue_growth", MathSupport.round(growthEffect));
        sensitivity.put("ebit_margin", MathSupport.round(marginEffect));
        sensitivity.put("wacc", MathSupport.round(waccEffect));
        sensitivity.put("terminal_growth", MathSupport.round(terminalEffect));
        sensitivity.put("target_pe", MathSupport.round(peEffect));
        sensitivity.put("target_pb", MathSupport.round(pbEffect));

        return new ScenarioResult(market.name(), output.result().symbol(), scenarios, sensitivity);
    }

    public List<HistoryPoint> history(Market market, String symbol, int days) {
        int boundedDays = Math.max(30, Math.min(days, 720));
        ValuationResult current = valuate(market, symbol);
        List<HistoryPoint> points = new ArrayList<>(boundedDays);

        for (int offset = boundedDays - 1; offset >= 0; offset--) {
            double index = boundedDays - offset;
            double priceWave = Math.sin(index / 14.0) * 0.035 + Math.cos(index / 31.0) * 0.020;
            double fairWave = Math.sin(index / 27.0) * 0.015;
            double closePrice = current.price() * (1.0 + priceWave);
            double fairValue = current.tradableFairValue() * (1.0 + fairWave);
            double deviation = closePrice / fairValue - 1.0;

            points.add(new HistoryPoint(
                    LocalDate.now().minusDays(offset),
                    MathSupport.round(closePrice),
                    MathSupport.round(fairValue),
                    MathSupport.round(deviation)
            ));
        }

        return points;
    }

    public List<ValuationResult> screener(ScreenerRequest request) {
        List<Market> markets = resolveMarkets(request.markets());
        double minUndervalued = request.minUndervalued() == null ? 0.0 : request.minUndervalued();
        double minRoe = request.minRoe() == null ? 0.0 : request.minRoe();
        double maxPb = request.maxPb() == null ? Double.MAX_VALUE : request.maxPb();
        double minDividend = request.minDividendYield() == null ? 0.0 : request.minDividendYield();
        boolean requirePositiveFcf = Boolean.TRUE.equals(request.requirePositiveFcf());
        int limit = request.limit() == null ? 20 : Math.min(request.limit(), 100);

        return marketDataService.listSnapshots(markets).stream()
                .filter(snapshot -> snapshot.fundamentals().roe() >= minRoe)
                .filter(snapshot -> snapshot.fundamentals().pb() <= maxPb)
                .filter(snapshot -> snapshot.fundamentals().dividendYield() >= minDividend)
                .filter(snapshot -> !requirePositiveFcf || snapshot.fundamentals().positiveFreeCashFlow())
                .map(snapshot -> valuate(snapshot.market(), snapshot.symbol()))
                .filter(valuation -> valuation.upside() >= minUndervalued)
                .sorted(Comparator.comparingDouble(ValuationResult::upside).reversed()
                        .thenComparing(Comparator.comparingDouble(ValuationResult::confidence).reversed()))
                .limit(limit)
                .toList();
    }

    private EngineOutput runValuation(Market market, String symbol) {
        StockSnapshot snapshot = marketDataService.getSnapshot(market, symbol);
        MarketComputation computation = strategyRegistry.get(market).evaluate(snapshot);

        double intrinsic = computation.models().stream()
                .mapToDouble(model -> model.value() * model.weight())
                .sum();

        double adjustmentImpact = computation.adjustments().stream()
                .mapToDouble(MarketAdjustment::impact)
                .sum();

        double tradable = intrinsic * (1.0 + adjustmentImpact);
        Map<String, Double> confidenceBreakdown = buildConfidenceBreakdown(snapshot, computation);
        double confidence = confidenceBreakdown.get("final_confidence");

        double band = MathSupport.clamp(
                0.09 + (1.0 - confidence) * 0.20 + snapshot.fundamentals().earningsVolatility() * 0.12,
                0.10,
                0.38
        );

        double fairLow = tradable * (1.0 - band);
        double fairHigh = tradable * (1.0 + band);
        double upside = tradable / snapshot.price() - 1.0;

        ValuationResult result = new ValuationResult(
                market,
                snapshot.symbol(),
                snapshot.currency(),
                MathSupport.round(snapshot.price()),
                LocalDate.now(),
                MathSupport.round(intrinsic),
                MathSupport.round(tradable),
                MathSupport.round(fairLow),
                MathSupport.round(fairHigh),
                MathSupport.round(upside),
                MathSupport.round(confidence),
                ValuationStatus.fromUpside(upside),
                computation.models(),
                computation.drivers(),
                computation.adjustments(),
                computation.riskFlags(),
                snapshot.dataVersion()
        );

        return new EngineOutput(result, computation, confidenceBreakdown, snapshot);
    }

    private Map<String, Double> buildConfidenceBreakdown(StockSnapshot snapshot, MarketComputation computation) {
        StockFundamentals f = snapshot.fundamentals();
        double freshness = MathSupport.clamp(1.0 - f.dataFreshnessDays() / 60.0, 0.20, 1.0);
        double stability = MathSupport.clamp(1.0 - f.earningsVolatility(), 0.15, 1.0);
        double liquidity = MathSupport.clamp(f.liquidityScore(), 0.10, 1.0);
        double consistency = modelConsistency(computation.models());

        double objectiveScore =
                f.dataCompleteness() * 0.30 +
                        freshness * 0.20 +
                        stability * 0.20 +
                        consistency * 0.15 +
                        liquidity * 0.15;

        double finalConfidence = MathSupport.clamp(computation.confidenceBase() * 0.55 + objectiveScore * 0.45, 0.25, 0.95);

        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("data_completeness", MathSupport.round(f.dataCompleteness()));
        breakdown.put("data_freshness", MathSupport.round(freshness));
        breakdown.put("earnings_stability", MathSupport.round(stability));
        breakdown.put("model_consistency", MathSupport.round(consistency));
        breakdown.put("liquidity", MathSupport.round(liquidity));
        breakdown.put("strategy_base", MathSupport.round(computation.confidenceBase()));
        breakdown.put("objective_score", MathSupport.round(objectiveScore));
        breakdown.put("final_confidence", MathSupport.round(finalConfidence));
        return breakdown;
    }

    private double modelConsistency(List<ModelValuation> models) {
        if (models.isEmpty()) {
            return 0.4;
        }
        double mean = models.stream().mapToDouble(ModelValuation::value).average().orElse(0.0);
        if (mean == 0.0) {
            return 0.4;
        }
        double variance = models.stream()
                .mapToDouble(model -> Math.pow(model.value() - mean, 2))
                .average()
                .orElse(0.0);
        double cv = Math.sqrt(variance) / mean;
        return MathSupport.clamp(1.0 - cv, 0.05, 1.0);
    }

    private ScenarioPoint toScenario(String name, double center, double band, double price) {
        double low = center * (1.0 - band);
        double high = center * (1.0 + band);
        return new ScenarioPoint(
                name,
                MathSupport.round(center),
                MathSupport.round(low),
                MathSupport.round(high),
                MathSupport.round(center / price - 1.0)
        );
    }

    private List<Market> resolveMarkets(List<String> rawMarkets) {
        if (rawMarkets == null || rawMarkets.isEmpty()) {
            return List.of(Market.US, Market.CN, Market.JP, Market.HK);
        }
        return rawMarkets.stream().map(Market::from).toList();
    }

    public record MarketSymbol(Market market, String symbol) {
    }

    private record EngineOutput(
            ValuationResult result,
            MarketComputation computation,
            Map<String, Double> confidenceBreakdown,
            StockSnapshot snapshot
    ) {
    }
}
