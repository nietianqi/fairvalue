package com.fairvalue.engine.jp;

import com.fairvalue.engine.api.dto.jp.JpCatalystFlag;
import com.fairvalue.engine.api.dto.jp.JpEventItem;
import com.fairvalue.engine.api.dto.jp.JpEventsResponse;
import com.fairvalue.engine.api.dto.jp.JpFairValueResponse;
import com.fairvalue.engine.api.dto.jp.JpHistoryPoint;
import com.fairvalue.engine.api.dto.jp.JpHistoryResponse;
import com.fairvalue.engine.api.dto.jp.JpMethodResult;
import com.fairvalue.engine.api.dto.jp.JpRecalcRequest;
import com.fairvalue.engine.api.dto.jp.JpRecalcResponse;
import com.fairvalue.engine.api.dto.jp.JpRiskFlag;
import com.fairvalue.engine.api.dto.jp.JpScenarioResult;
import com.fairvalue.engine.api.dto.jp.JpScreenerItem;
import com.fairvalue.engine.api.dto.jp.JpScreenerRequest;
import com.fairvalue.engine.api.dto.jp.JpScreenerResponse;
import com.fairvalue.engine.api.dto.jp.JpStockOverviewResponse;
import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.domain.StockFundamentals;
import com.fairvalue.engine.domain.StockSnapshot;
import com.fairvalue.engine.service.MarketDataService;
import com.fairvalue.engine.service.ValuationService;
import com.fairvalue.engine.valuation.HistoryPoint;
import com.fairvalue.engine.valuation.MarketAdjustment;
import com.fairvalue.engine.valuation.ValuationResult;
import com.fairvalue.engine.valuation.strategy.MathSupport;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class JpEquityValuationService {
    private final MarketDataService marketDataService;
    private final ValuationService valuationService;

    public JpEquityValuationService(MarketDataService marketDataService, ValuationService valuationService) {
        this.marketDataService = marketDataService;
        this.valuationService = valuationService;
    }

    public JpStockOverviewResponse overview(String code) {
        JpFairValueResponse fairValue = fairValue(code);
        return new JpStockOverviewResponse(
                fairValue.code(),
                fairValue.companyName(),
                fairValue.market(),
                fairValue.currency(),
                fairValue.currentPrice(),
                fairValue.fairValueMid(),
                fairValue.fairValueLow(),
                fairValue.fairValueHigh(),
                fairValue.valuationLabel(),
                fairValue.confidenceLevel(),
                fairValue.upsideDownsidePct()
        );
    }

    public JpFairValueResponse fairValue(String code) {
        StockSnapshot snapshot = marketDataService.getSnapshot(Market.JP, code);
        ValuationResult valuation = valuationService.valuate(Market.JP, code);
        StockFundamentals f = snapshot.fundamentals();

        List<JpMethodResult> methodResults = valuation.models().stream()
                .map(model -> new JpMethodResult(model.name(), model.value(), model.weight(), model.rationale()))
                .toList();

        Map<String, Double> weights = new LinkedHashMap<>();
        valuation.models().forEach(model -> weights.put(model.name(), model.weight()));

        Map<String, Double> assumptions = new LinkedHashMap<>();
        assumptions.put("wacc", MathSupport.round(f.wacc()));
        assumptions.put("terminal_growth", MathSupport.round(f.terminalGrowth()));
        assumptions.put("normalized_roe", MathSupport.round(f.roe()));
        assumptions.put("normalized_ev_ebitda", MathSupport.round(f.evEbitda()));
        assumptions.put("payout_yield", MathSupport.round(f.dividendYield() + f.buybackYield()));

        List<JpRiskFlag> riskFlags = toRiskFlags(valuation.riskFlags());
        List<JpCatalystFlag> catalystFlags = toCatalysts(snapshot, valuation.marketAdjustments());

        int confidenceScore = (int) Math.round(valuation.confidence() * 100.0);
        double marginOfSafety = marginOfSafety(confidenceScore, f);
        List<JpScenarioResult> scenarios = buildScenarios(valuation, marginOfSafety, riskFlags);

        return new JpFairValueResponse(
                snapshot.symbol(),
                snapshot.companyName(),
                snapshot.market().name(),
                snapshot.currency(),
                snapshot.industry(),
                valuation.price(),
                valuation.fairValueLow(),
                valuation.tradableFairValue(),
                valuation.fairValueHigh(),
                valuation.tradableFairValue(),
                valuationLabel(valuation.upside()),
                confidenceScore,
                confidenceLevel(confidenceScore),
                valuation.upside(),
                marginOfSafety,
                methodResults.stream().map(JpMethodResult::method).toList(),
                methodResults,
                scenarios,
                weights,
                assumptions,
                riskFlags,
                catalystFlags,
                valuation.dataVersion()
        );
    }

    public JpHistoryResponse history(String code, int days) {
        List<HistoryPoint> points = valuationService.history(Market.JP, code, days);
        List<JpHistoryPoint> mapped = points.stream()
                .map(point -> new JpHistoryPoint(point.date(), point.closePrice(), point.tradableFairValue(), point.deviation(), point.simulated()))
                .toList();
        return new JpHistoryResponse(code.toUpperCase(Locale.ROOT), mapped);
    }

    public JpScreenerResponse screener(JpScreenerRequest request) {
        JpScreenerRequest payload = normalize(request);
        double minUndervalued = toPct(payload.minUndervaluedPct());

        List<JpScreenerItem> items = marketDataService.listSnapshots(List.of(Market.JP)).stream()
                .filter(snapshot -> payload.industry() == null || payload.industry().isBlank() || snapshot.industry().toLowerCase(Locale.ROOT).contains(payload.industry().toLowerCase(Locale.ROOT)))
                .filter(snapshot -> snapshot.fundamentals().dividendYield() >= payload.minDividendYield())
                .filter(snapshot -> snapshot.fundamentals().pb() <= payload.maxPbr())
                .map(snapshot -> {
                    ValuationResult result = valuationService.valuate(Market.JP, snapshot.symbol());
                    int confidence = (int) Math.round(result.confidence() * 100.0);
                    return new JpScreenerItem(
                            snapshot.symbol(),
                            snapshot.companyName(),
                            snapshot.industry(),
                            result.price(),
                            result.tradableFairValue(),
                            result.upside(),
                            confidence,
                            snapshot.fundamentals().dividendYield()
                    );
                })
                .filter(item -> item.undervaluedPct() >= minUndervalued)
                .filter(item -> item.confidenceScore() >= payload.minConfidenceScore())
                .sorted(Comparator.comparingDouble(JpScreenerItem::undervaluedPct).reversed()
                        .thenComparing(Comparator.comparingInt(JpScreenerItem::confidenceScore).reversed()))
                .limit(payload.limit())
                .toList();

        return new JpScreenerResponse(items.size(), items);
    }

    public JpRecalcResponse recalc(JpRecalcRequest request) {
        List<String> targetCodes;
        if (request == null || request.codes() == null || request.codes().isEmpty()) {
            targetCodes = marketDataService.listSnapshots(List.of(Market.JP)).stream().map(StockSnapshot::symbol).toList();
        } else {
            targetCodes = request.codes().stream().map(code -> code.toUpperCase(Locale.ROOT)).toList();
        }

        int recalculated = 0;
        for (String code : targetCodes) {
            valuationService.valuate(Market.JP, code);
            recalculated += 1;
        }

        String runId = "JP-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return new JpRecalcResponse(runId, targetCodes.size(), recalculated, targetCodes);
    }

    public JpEventsResponse events(String code) {
        StockSnapshot snapshot = marketDataService.getSnapshot(Market.JP, code);
        StockFundamentals f = snapshot.fundamentals();

        List<JpEventItem> events = new ArrayList<>();
        events.add(new JpEventItem(LocalDate.now().minusDays((long) f.dataFreshnessDays()), "financial_update", "Latest financial snapshot update", "neutral"));

        if (f.dividendYield() > 0.02) {
            events.add(new JpEventItem(LocalDate.now().plusDays(12), "dividend", "Dividend policy review window", "positive"));
        }
        if (f.buybackYield() > 0.01) {
            events.add(new JpEventItem(LocalDate.now().plusDays(20), "buyback", "Potential buyback pace confirmation", "positive"));
        }
        if (f.earningsVolatility() > 0.28) {
            events.add(new JpEventItem(LocalDate.now().plusDays(7), "fx_watch", "FX sensitivity and guidance check", "negative"));
        }
        if (snapshot.fundamentals().governanceScore() > 0.65 && snapshot.fundamentals().pb() < 1.3) {
            events.add(new JpEventItem(LocalDate.now().plusDays(30), "governance", "TSE capital efficiency action watch", "positive"));
        }

        return new JpEventsResponse(snapshot.symbol(), events);
    }

    private List<JpRiskFlag> toRiskFlags(List<String> flags) {
        return flags.stream().map(flag -> switch (flag) {
            case "value_trap_risk" -> new JpRiskFlag("high", "valuation", "Low PB but weak governance/return policy may indicate value trap risk.");
            case "thin_liquidity" -> new JpRiskFlag("medium", "liquidity", "Thin trading depth can delay fair-value convergence.");
            case "fx_earnings_sensitivity" -> new JpRiskFlag("medium", "macro", "Earnings are sensitive to FX assumptions and guidance updates.");
            case "weak_shareholder_return_policy" -> new JpRiskFlag("medium", "governance", "Dividend and buyback commitment appears weaker than peers.");
            default -> new JpRiskFlag("low", "general", flag);
        }).toList();
    }

    private List<JpCatalystFlag> toCatalysts(StockSnapshot snapshot, List<MarketAdjustment> adjustments) {
        StockFundamentals f = snapshot.fundamentals();
        List<JpCatalystFlag> catalysts = new ArrayList<>();

        catalysts.add(new JpCatalystFlag("governance", f.governanceScore() > 0.65 ? "reform_progressing" : "watch", "medium"));
        catalysts.add(new JpCatalystFlag("shareholder_return", (f.buybackYield() + f.dividendYield()) > 0.03 ? "supportive" : "neutral", "medium"));

        double totalAdj = adjustments.stream().mapToDouble(MarketAdjustment::impact).sum();
        if (totalAdj > 0.03) {
            catalysts.add(new JpCatalystFlag("valuation_repair", "positive_adjustment_stack", "high"));
        }
        if (f.earningsVolatility() > 0.30) {
            catalysts.add(new JpCatalystFlag("earnings_visibility", "volatile", "negative"));
        }

        return catalysts;
    }

    private String confidenceLevel(int score) {
        if (score >= 80) {
            return "high";
        }
        if (score >= 65) {
            return "medium";
        }
        return "low";
    }

    private String valuationLabel(double upside) {
        if (upside >= 0.20) {
            return "undervalued";
        }
        if (upside >= 0.05) {
            return "slightly_undervalued";
        }
        if (upside <= -0.20) {
            return "overvalued";
        }
        if (upside <= -0.05) {
            return "slightly_overvalued";
        }
        return "fair";
    }

    private double marginOfSafety(int confidenceScore, StockFundamentals f) {
        if (confidenceScore >= 82 && f.earningsVolatility() < 0.22) {
            return 0.18;
        }
        if (f.earningsVolatility() > 0.32) {
            return 0.35;
        }
        return 0.25;
    }

    private List<JpScenarioResult> buildScenarios(ValuationResult valuation, double marginOfSafety, List<JpRiskFlag> risks) {
        long highRiskCount = risks.stream().filter(risk -> "high".equals(risk.level())).count();
        double bear = MathSupport.clamp(0.25 + highRiskCount * 0.05, 0.20, 0.45);
        double bull = MathSupport.clamp(0.20 - highRiskCount * 0.03, 0.10, 0.30);
        double base = MathSupport.round(1.0 - bear - bull);

        double bearMid = valuation.fairValueLow();
        double baseMid = valuation.tradableFairValue();
        double bullMid = valuation.fairValueHigh();

        return List.of(
                new JpScenarioResult(
                        "bear",
                        MathSupport.round(bear),
                        bearMid,
                        MathSupport.round(bearMid * 0.92),
                        MathSupport.round(bearMid * 1.08),
                        MathSupport.round(marginOfSafety)
                ),
                new JpScenarioResult(
                        "base",
                        MathSupport.round(base),
                        baseMid,
                        valuation.fairValueLow(),
                        valuation.fairValueHigh(),
                        MathSupport.round(marginOfSafety)
                ),
                new JpScenarioResult(
                        "bull",
                        MathSupport.round(bull),
                        bullMid,
                        MathSupport.round(baseMid * 0.95),
                        MathSupport.round(bullMid * 1.12),
                        MathSupport.round(Math.max(0.10, marginOfSafety - 0.05))
                )
        );
    }

    private double toPct(Double input) {
        if (input == null) {
            return 0.0;
        }
        if (input > 1.0) {
            return input / 100.0;
        }
        return input;
    }

    private JpScreenerRequest normalize(JpScreenerRequest request) {
        if (request == null) {
            return new JpScreenerRequest("JP", 0.0, 0, 0.0, Double.MAX_VALUE, null, 20);
        }
        return new JpScreenerRequest(
                request.market() == null ? "JP" : request.market(),
                request.minUndervaluedPct() == null ? 0.0 : request.minUndervaluedPct(),
                request.minConfidenceScore() == null ? 0 : request.minConfidenceScore(),
                request.minDividendYield() == null ? 0.0 : request.minDividendYield(),
                request.maxPbr() == null ? Double.MAX_VALUE : request.maxPbr(),
                request.industry(),
                request.limit() == null ? 20 : Math.min(request.limit(), 200)
        );
    }
}
