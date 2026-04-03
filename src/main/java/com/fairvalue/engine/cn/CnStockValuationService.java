package com.fairvalue.engine.cn;

import com.fairvalue.engine.api.dto.cn.CnDiscoveryItem;
import com.fairvalue.engine.api.dto.cn.CnDiscoveryResponse;
import com.fairvalue.engine.api.dto.cn.CnMethodResult;
import com.fairvalue.engine.api.dto.cn.CnOperationZones;
import com.fairvalue.engine.api.dto.cn.CnPriceBand;
import com.fairvalue.engine.api.dto.cn.CnRiskAdjustment;
import com.fairvalue.engine.api.dto.cn.CnRulesVersionResponse;
import com.fairvalue.engine.api.dto.cn.CnScenarioResult;
import com.fairvalue.engine.api.dto.cn.CnValuationReportResponse;
import com.fairvalue.engine.api.dto.cn.CnValuationRunRequest;
import com.fairvalue.engine.api.dto.cn.CnValuationRunResponse;
import com.fairvalue.engine.api.dto.cn.CnValuationSummaryResponse;
import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.domain.StockFundamentals;
import com.fairvalue.engine.domain.StockSnapshot;
import com.fairvalue.engine.service.MarketDataService;
import com.fairvalue.engine.valuation.strategy.MathSupport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CnStockValuationService {
    private final MarketDataService marketDataService;
    private final CnRulesProvider rulesProvider;
    private final CnEastmoneyClient cnEastmoneyClient;

    public CnStockValuationService(
            MarketDataService marketDataService,
            CnRulesProvider rulesProvider,
            CnEastmoneyClient cnEastmoneyClient
    ) {
        this.marketDataService = marketDataService;
        this.rulesProvider = rulesProvider;
        this.cnEastmoneyClient = cnEastmoneyClient;
    }

    public CnRulesVersionResponse rulesVersion() {
        return new CnRulesVersionResponse(
                rulesProvider.name(),
                rulesProvider.version(),
                rulesProvider.minimumModelCount(),
                rulesProvider.requiredScenarios(),
                rulesProvider.masterFormula()
        );
    }

    public CnValuationRunResponse run(String ticker, CnValuationRunRequest request) {
        CnValuationRunRequest payload = normalize(request);
        Market market = resolveMarket(payload.market());
        StockSnapshot snapshot = marketDataService.getSnapshot(market, ticker);
        return buildRunResponse(market, snapshot, payload);
    }

    public CnValuationSummaryResponse summary(String ticker, CnValuationRunRequest request) {
        CnValuationRunResponse run = run(ticker, request);
        double upside = run.fundamentalFairValue() / run.currentPrice() - 1.0;

        return new CnValuationSummaryResponse(
                run.ticker(),
                run.market(),
                run.currentPrice(),
                run.fundamentalFairValue(),
                run.fairValueLow(),
                run.fairValueHigh(),
                MathSupport.round(upside),
                run.confidenceScore(),
                verdict(upside)
        );
    }

    public CnValuationReportResponse report(String ticker, CnValuationRunRequest request) {
        CnValuationRunResponse run = run(ticker, request);
        Map<String, String> sections = new LinkedHashMap<>();

        sections.put("company_and_industry_positioning", "公司类型=" + run.companyType() + "；行业路由=" + run.industryRoute());
        sections.put("latest_key_data", "当前价=" + run.currentPrice() + "，中性公允价值=" + run.fundamentalFairValue());
        sections.put("financial_quality_assessment", "置信度=" + run.confidenceScore() + "，结合盈利质量、现金流和波动性评估。");
        sections.put("valuation_method_1", run.methodResults().get(0).methodName() + " 权重=" + run.methodResults().get(0).weight());
        sections.put("valuation_method_2", run.methodResults().size() > 1 ? run.methodResults().get(1).methodName() + " 权重=" + run.methodResults().get(1).weight() : "N/A");
        sections.put("valuation_method_3", run.methodResults().size() > 2 ? run.methodResults().get(2).methodName() + " 权重=" + run.methodResults().get(2).weight() : "N/A");
        sections.put("integrated_valuation_conclusion", "综合估值区间=" + run.fairValueLow() + "~" + run.fairValueHigh());
        sections.put("three_layer_price_system", "安全边际价=" + run.marginOfSafetyPrice() + "；公允价值=" + run.fundamentalFairValue() + "；情绪上沿价=" + run.sentimentUpperBandPrice());
        sections.put("current_price_positioning", run.currentPricePositioning());
        sections.put("catalysts_and_risks", "重点关注政策变化、风格切换、盈利兑现和流动性扰动。");
        sections.put("suitable_investor_type", "适合希望结合估值区间、风险标签和安全边际做分层决策的投资者。");
        sections.put("final_conclusion_and_confidence_score", verdict(run.fundamentalFairValue() / run.currentPrice() - 1.0) + "，置信度=" + run.confidenceScore());

        return new CnValuationReportResponse(
                run.ticker(),
                run.market(),
                summary(run.ticker(), request).verdict(),
                run.methodResults(),
                run.scenarios(),
                run.riskAdjustments(),
                run.operationZones(),
                run.confidenceScore(),
                sections
        );
    }

    public CnDiscoveryResponse discovery(Integer page, Integer size) {
        CnEastmoneyClient.CnUniversePage universePage = cnEastmoneyClient.fetchUniversePage(page, size);
        if (!universePage.items().isEmpty()) {
            List<CnDiscoveryItem> items = universePage.items().stream()
                    .map(item -> {
                        StockSnapshot snapshot = marketDataService.snapshotFromCnUniverseItem(item);
                        CnValuationRunResponse run = buildRunResponse(Market.CN, snapshot, normalize(null));
                        return toDiscoveryItem(snapshot, run, item.marketCap(), item.changePercent(), item.turnoverAmount());
                    })
                    .toList();

            return new CnDiscoveryResponse(
                    universePage.page(),
                    universePage.size(),
                    universePage.total(),
                    universePage.source(),
                    items
            );
        }

        List<StockSnapshot> fallback = marketDataService.listSnapshots(List.of(Market.CN));
        List<CnDiscoveryItem> items = fallback.stream()
                .map(snapshot -> {
                    CnValuationRunResponse run = buildRunResponse(Market.CN, snapshot, normalize(null));
                    return toDiscoveryItem(snapshot, run, null, 0.0, 0.0);
                })
                .toList();

        return new CnDiscoveryResponse(1, items.size(), items.size(), "seed-fallback", items);
    }

    private CnValuationRunResponse buildRunResponse(Market market, StockSnapshot snapshot, CnValuationRunRequest payload) {
        StockFundamentals f = snapshot.fundamentals();
        String companyType = classifyCompanyType(snapshot);
        String industryRoute = classifyIndustryRoute(snapshot);

        List<String> routedMethods = routedMethods(companyType, industryRoute);
        Map<String, Double> methodValues = estimateMethodValues(snapshot, routedMethods, payload.overrideAssumptions());
        Map<String, Double> methodWeights = resolveWeights(companyType, industryRoute, methodValues.keySet());

        List<CnMethodResult> methods = methodValues.entrySet().stream()
                .map(entry -> new CnMethodResult(
                        entry.getKey(),
                        MathSupport.round(entry.getValue()),
                        MathSupport.round(methodWeights.getOrDefault(entry.getKey(), 0.0)),
                        methodAssumption(entry.getKey())
                ))
                .sorted(Comparator.comparingDouble(CnMethodResult::weight).reversed())
                .toList();

        double weightedValue = methods.stream().mapToDouble(method -> method.methodValue() * method.weight()).sum();

        CnAdjustment adjustment = chinaAdjustment(snapshot);
        double fairMid = weightedValue * adjustment.totalFactor();

        double confidence01 = confidenceScore01(snapshot, methods, adjustment);
        int confidence = (int) Math.round(confidence01 * 100.0);

        double band = MathSupport.clamp(0.10 + (1.0 - confidence01) * 0.18 + f.earningsVolatility() * 0.10, 0.10, 0.40);
        double fairLow = fairMid * (1.0 - band);
        double fairHigh = fairMid * (1.0 + band);

        double marginOfSafetyDiscount = marginOfSafetyDiscount(snapshot, confidence01);
        double marginOfSafetyPrice = fairMid * (1.0 - marginOfSafetyDiscount);
        double sentimentUpper = fairHigh * (1.0 + (adjustment.styleFactor() - 1.0) * 0.50 + Math.max(0.0, adjustment.policyFactor() - 1.0) * 0.40);

        List<CnRiskAdjustment> risks = riskAdjustments(snapshot, adjustment);
        List<CnScenarioResult> scenarios = scenarios(fairLow, fairMid, fairHigh, risks);
        CnOperationZones zones = operationZones(marginOfSafetyPrice, fairMid, fairHigh);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fundamental_fair_value", MathSupport.round(fairMid));
        summary.put("china_adjustment_factor", MathSupport.round(adjustment.totalFactor()));
        summary.put("current_price", MathSupport.round(snapshot.price()));
        summary.put("upside", MathSupport.round(fairMid / snapshot.price() - 1.0));
        summary.put("data_version", snapshot.dataVersion());

        return new CnValuationRunResponse(
                snapshot.symbol(),
                market.name(),
                companyType,
                industryRoute,
                MathSupport.round(snapshot.price()),
                MathSupport.round(fairMid),
                MathSupport.round(fairLow),
                MathSupport.round(fairHigh),
                MathSupport.round(marginOfSafetyPrice),
                MathSupport.round(sentimentUpper),
                pricePosition(snapshot.price(), fairLow, fairMid, fairHigh),
                methods,
                scenarios,
                risks,
                zones,
                confidence,
                rulesProvider.requiredSections(),
                summary
        );
    }

    private CnDiscoveryItem toDiscoveryItem(
            StockSnapshot snapshot,
            CnValuationRunResponse run,
            Double marketCap,
            Double dailyChange,
            Double turnover
    ) {
        double upside = run.fundamentalFairValue() / Math.max(run.currentPrice(), 0.01) - 1.0;
        int confidence = run.confidenceScore();
        String cashflowRating = snapshot.fundamentals().positiveFreeCashFlow() ? gradeLabel(Math.max(confidence - 5, 45)) : "差";

        return new CnDiscoveryItem(
                snapshot.symbol(),
                snapshot.companyName(),
                snapshot.industry(),
                run.currentPrice(),
                run.fundamentalFairValue(),
                run.fairValueLow(),
                run.fairValueHigh(),
                MathSupport.round(upside),
                confidence,
                verdict(upside),
                gradeLabel(confidence),
                cashflowRating,
                gradeLabel(Math.min(confidence + 3, 95)),
                gradeLabel(Math.min(confidence + 1, 95)),
                analystRating(upside, confidence),
                MathSupport.round(snapshot.fundamentals().pe()),
                MathSupport.round(snapshot.fundamentals().evEbitda()),
                MathSupport.round(marketCapInYi(marketCap, snapshot)),
                MathSupport.round(snapshot.fundamentals().revenueGrowth()),
                MathSupport.round(dailyChange == null ? 0.0 : dailyChange),
                MathSupport.round(turnover == null ? 0.0 : turnover)
        );
    }

    private CnValuationRunRequest normalize(CnValuationRunRequest request) {
        if (request == null) {
            return new CnValuationRunRequest("CN", "CNY", Map.of(), "full");
        }
        String mode = request.outputMode() == null || request.outputMode().isBlank() ? "full" : request.outputMode();
        return new CnValuationRunRequest(
                request.market() == null ? "CN" : request.market(),
                request.currency() == null ? "CNY" : request.currency(),
                request.overrideAssumptions() == null ? Map.of() : request.overrideAssumptions(),
                mode
        );
    }

    private Market resolveMarket(String raw) {
        Market market = Market.from(raw);
        return switch (market) {
            case CN, HK, US -> market;
            default -> Market.CN;
        };
    }

    private String classifyCompanyType(StockSnapshot snapshot) {
        StockFundamentals f = snapshot.fundamentals();
        if (!f.positiveFreeCashFlow() && f.revenueGrowth() > 0.15) {
            return "loss_making_expansion";
        }
        if (f.dividendYield() > 0.035 && f.revenueGrowth() < 0.08) {
            return "high_dividend_defensive";
        }
        if (f.revenueGrowth() > 0.18 && f.roe() > 0.12) {
            return "high_growth";
        }
        if (f.earningsVolatility() > 0.35) {
            return "strong_cyclical";
        }
        if (f.pb() < 1.0 && f.netCashToMarketCap() > 0.10) {
            return "asset_revaluation";
        }
        return "mature_stable";
    }

    private String classifyIndustryRoute(StockSnapshot snapshot) {
        String industry = snapshot.industry().toLowerCase(Locale.ROOT);
        String symbol = snapshot.symbol().toUpperCase(Locale.ROOT);

        if (industry.contains("financial") || symbol.startsWith("60") && symbol.endsWith("6") && snapshot.market() == Market.CN) {
            return "bank";
        }
        if (industry.contains("insurance")) {
            return "insurance";
        }
        if (industry.contains("consumer")) {
            return "consumer";
        }
        if (industry.contains("semiconductor")) {
            return "semiconductor";
        }
        if (industry.contains("technology")) {
            return "ai_software_saas";
        }
        if (industry.contains("industrial") || industry.contains("resource")) {
            return "cyclical_resources";
        }
        return "consumer";
    }

    private List<String> routedMethods(String companyType, String industryRoute) {
        Set<String> methods = new LinkedHashSet<>();
        rulesProvider.methodsForIndustry(industryRoute).forEach(method -> methods.add(rulesProvider.normalize(method)));
        rulesProvider.methodsForCompanyType(companyType).forEach(method -> methods.add(rulesProvider.normalize(method)));

        if (methods.isEmpty()) {
            methods.add("PE");
            methods.add("DCF");
            methods.add("EV_EBITDA");
        }

        List<String> prioritized = methods.stream().limit(6).toList();
        if (prioritized.size() >= rulesProvider.minimumModelCount()) {
            return prioritized;
        }

        List<String> fallback = new ArrayList<>(prioritized);
        fallback.addAll(List.of("PE", "DCF", "EV_EBITDA", "PB"));
        return fallback.stream().distinct().limit(Math.max(4, rulesProvider.minimumModelCount())).toList();
    }

    private Map<String, Double> estimateMethodValues(StockSnapshot snapshot, List<String> methods, Map<String, Double> overrides) {
        StockFundamentals f = snapshot.fundamentals();
        double price = snapshot.price();

        double wacc = overrides.getOrDefault("wacc", MathSupport.clamp(0.085 + f.policySensitivity() * 0.04, 0.08, 0.16));
        double terminal = overrides.getOrDefault("terminal_growth", MathSupport.clamp(0.018 + f.revenueGrowth() * 0.20, 0.012, 0.04));

        Map<String, Double> values = new LinkedHashMap<>();
        for (String method : methods) {
            double value = switch (method) {
                case "PE", "NORMALIZED_PE", "PE_CORE", "MID_CYCLE_PE" ->
                        price * MathSupport.clamp((18.0 + f.roe() * 40.0) / Math.max(f.pe(), 6.0), 0.70, 1.45);
                case "PEG" -> {
                    double peg = Math.max(f.pe() / Math.max(f.revenueGrowth() * 100.0, 4.0), 0.3);
                    yield price * MathSupport.clamp(1.10 / peg, 0.65, 1.45);
                }
                case "PB", "PB_ROE_KE" ->
                        price * MathSupport.clamp(1.0 + f.roe() * 0.70 - Math.max(0.0, wacc - 0.09) * 2.2, 0.65, 1.40);
                case "PS", "EV_SALES", "EV_ARR" ->
                        price * MathSupport.clamp(0.90 + f.revenueGrowth() * 1.8 + (f.positiveFreeCashFlow() ? 0.08 : -0.05), 0.60, 1.55);
                case "EV_EBITDA", "MID_CYCLE_EV_EBITDA", "MID_CYCLE_PROFIT" ->
                        price * MathSupport.clamp(11.0 / Math.max(f.evEbitda(), 4.0), 0.65, 1.42);
                case "DCF", "STAGE_DCF", "DDM" ->
                        price * MathSupport.clamp(1.0 + f.fcfMargin() * 1.2 + f.revenueGrowth() * 0.8 - wacc + terminal * 2.2, 0.65, 1.55);
                case "NAV", "SOTP" ->
                        price * MathSupport.clamp(1.0 + Math.max(f.netCashToMarketCap(), -0.10) * 0.8 + (1.0 / Math.max(f.pb(), 0.8) - 0.7) * 0.25, 0.62, 1.65);
                case "RNPV", "EMBEDDED_VALUE", "NBV_MULTIPLE" ->
                        price * MathSupport.clamp(0.95 + f.revenueGrowth() * 0.7 - f.earningsVolatility() * 0.35, 0.60, 1.40);
                case "P_PPOP" ->
                        price * MathSupport.clamp(1.0 + f.roe() * 0.45 - f.policySensitivity() * 0.20, 0.70, 1.30);
                default ->
                        price * MathSupport.clamp(1.0 + f.revenueGrowth() * 0.5 - f.earningsVolatility() * 0.2, 0.70, 1.30);
            };
            values.put(method, MathSupport.round(value));
        }
        return values;
    }

    private Map<String, Double> resolveWeights(String companyType, String industryRoute, Set<String> methods) {
        Map<String, Double> caseWeights = rulesProvider.weightingForCase(weightingCase(companyType, industryRoute));
        Map<String, Double> weights = new LinkedHashMap<>();

        if (!caseWeights.isEmpty()) {
            for (String method : methods) {
                weights.put(method, caseWeights.getOrDefault(method, 0.0));
            }
        }

        double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum <= 0.0) {
            double equal = 1.0 / methods.size();
            methods.forEach(method -> weights.put(method, equal));
            return weights;
        }

        Map<String, Double> normalized = new LinkedHashMap<>();
        methods.forEach(method -> normalized.put(method, weights.get(method) / sum));
        return normalized;
    }

    private String weightingCase(String companyType, String industryRoute) {
        if ("bank".equals(industryRoute)) {
            return "银行";
        }
        if ("semiconductor".equals(industryRoute)) {
            return "半导体";
        }
        if ("holding_group".equals(industryRoute)) {
            return "集团控股";
        }
        if ("consumer".equals(industryRoute) && ("mature_stable".equals(companyType) || "high_growth".equals(companyType))) {
            return "成熟消费";
        }
        return "";
    }

    private CnAdjustment chinaAdjustment(StockSnapshot snapshot) {
        StockFundamentals f = snapshot.fundamentals();
        double policyFactor = 1.0 - f.policySensitivity() * 0.20 + f.themePremium() * 0.06;
        double liquidityFactor = 0.85 + f.liquidityScore() * 0.18;
        double governanceFactor = 0.82 + f.governanceScore() * 0.21;
        double structureFactor = 1.0 - Math.max(0.0, f.sbcRatio() - 0.02) * 1.5;
        double styleFactor = 0.95 + f.themePremium() * 0.12;

        policyFactor = MathSupport.clamp(policyFactor, 0.60, 1.10);
        liquidityFactor = MathSupport.clamp(liquidityFactor, 0.70, 1.02);
        governanceFactor = MathSupport.clamp(governanceFactor, 0.60, 1.03);
        structureFactor = MathSupport.clamp(structureFactor, 0.70, 1.00);
        styleFactor = MathSupport.clamp(styleFactor, 0.90, 1.08);

        return new CnAdjustment(
                policyFactor,
                liquidityFactor,
                governanceFactor,
                structureFactor,
                styleFactor
        );
    }

    private List<CnRiskAdjustment> riskAdjustments(StockSnapshot snapshot, CnAdjustment adjustment) {
        StockFundamentals f = snapshot.fundamentals();
        List<CnRiskAdjustment> list = new ArrayList<>();

        list.add(new CnRiskAdjustment(
                "policy_factor",
                level(adjustment.policyFactor(), 0.90, 1.00),
                MathSupport.round(adjustment.policyFactor() - 1.0),
                "政策环境会直接影响估值修复速度与风险溢价。"
        ));
        list.add(new CnRiskAdjustment(
                "liquidity_factor",
                level(adjustment.liquidityFactor(), 0.90, 0.97),
                MathSupport.round(adjustment.liquidityFactor() - 1.0),
                "流动性分层会影响可交易公允价值。"
        ));
        list.add(new CnRiskAdjustment(
                "governance_factor",
                level(adjustment.governanceFactor(), 0.85, 0.95),
                MathSupport.round(adjustment.governanceFactor() - 1.0),
                "治理质量与信息透明度决定估值持续性。"
        ));

        if (!f.positiveFreeCashFlow()) {
            list.add(new CnRiskAdjustment(
                    "cashflow_red_flag",
                    "High",
                    -0.05,
                    "自由现金流为负，需警惕价值陷阱。"
            ));
        }

        if (f.sbcRatio() > 0.06) {
            list.add(new CnRiskAdjustment(
                    "dilution_pressure",
                    "Medium",
                    -0.03,
                    "潜在稀释压力会压缩每股公允价值。"
            ));
        }

        return list;
    }

    private List<CnScenarioResult> scenarios(double fairLow, double fairMid, double fairHigh, List<CnRiskAdjustment> risks) {
        Map<String, Double> weights = new LinkedHashMap<>(rulesProvider.defaultScenarioWeights());
        long highCount = risks.stream().filter(risk -> "High".equals(risk.level())).count();

        if (highCount > 0) {
            weights.put("bear", MathSupport.clamp(weights.get("bear") + 0.05 * highCount, 0.25, 0.45));
            weights.put("bull", MathSupport.clamp(weights.get("bull") - 0.03 * highCount, 0.08, 0.22));
            weights.put("base", MathSupport.round(1.0 - weights.get("bear") - weights.get("bull")));
        }

        double bearMid = fairLow;
        double baseMid = fairMid;
        double bullMid = fairHigh;

        return List.of(
                new CnScenarioResult("bear", MathSupport.round(weights.getOrDefault("bear", 0.25)), MathSupport.round(bearMid * 0.90), MathSupport.round(bearMid), MathSupport.round(bearMid * 1.08)),
                new CnScenarioResult("base", MathSupport.round(weights.getOrDefault("base", 0.55)), MathSupport.round(baseMid * 0.92), MathSupport.round(baseMid), MathSupport.round(baseMid * 1.08)),
                new CnScenarioResult("bull", MathSupport.round(weights.getOrDefault("bull", 0.20)), MathSupport.round(bullMid * 0.93), MathSupport.round(bullMid), MathSupport.round(bullMid * 1.12))
        );
    }

    private CnOperationZones operationZones(double marginOfSafetyPrice, double fairMid, double fairHigh) {
        List<CnPriceBand> zones = List.of(
                new CnPriceBand("strong_buy", MathSupport.round(marginOfSafetyPrice * 0.85), MathSupport.round(marginOfSafetyPrice)),
                new CnPriceBand("buy", MathSupport.round(marginOfSafetyPrice), MathSupport.round(fairMid * 0.90)),
                new CnPriceBand("hold", MathSupport.round(fairMid * 0.90), MathSupport.round(fairMid * 1.05)),
                new CnPriceBand("reduce", MathSupport.round(fairMid * 1.05), MathSupport.round(fairHigh)),
                new CnPriceBand("avoid", MathSupport.round(fairHigh), MathSupport.round(fairHigh * 1.15))
        );
        return new CnOperationZones(zones);
    }

    private double confidenceScore01(StockSnapshot snapshot, List<CnMethodResult> methods, CnAdjustment adjustment) {
        StockFundamentals f = snapshot.fundamentals();
        double dataQuality = f.dataCompleteness();
        double financialQuality = MathSupport.clamp(0.5 + f.fcfMargin() * 0.7 + f.roe() * 0.5 - f.earningsVolatility() * 0.4, 0.10, 1.0);
        double businessVisibility = MathSupport.clamp(0.35 + f.analystCoverage() * 0.55 + (f.positiveFreeCashFlow() ? 0.10 : -0.10), 0.10, 1.0);
        double modelFit = MathSupport.clamp(methods.size() / 5.0, 0.5, 1.0);
        double policyGovernance = MathSupport.clamp((adjustment.policyFactor() + adjustment.governanceFactor()) / 2.0, 0.20, 1.0);
        double forecastCertainty = MathSupport.clamp(1.0 - f.earningsVolatility() * 0.9, 0.10, 1.0);

        return MathSupport.clamp(
                dataQuality * 0.25 +
                        financialQuality * 0.20 +
                        businessVisibility * 0.20 +
                        modelFit * 0.15 +
                        policyGovernance * 0.10 +
                        forecastCertainty * 0.10,
                0.20,
                0.95
        );
    }

    private double marginOfSafetyDiscount(StockSnapshot snapshot, double confidence01) {
        StockFundamentals f = snapshot.fundamentals();
        if (confidence01 > 0.78 && f.earningsVolatility() < 0.25) {
            return 0.18;
        }
        if (f.earningsVolatility() > 0.40 || !f.positiveFreeCashFlow()) {
            return 0.35;
        }
        return 0.25;
    }

    private String methodAssumption(String method) {
        return switch (method) {
            case "PE", "NORMALIZED_PE", "PE_CORE", "MID_CYCLE_PE" -> "使用相对估值锚，衡量当前盈利与市场给予的估值倍数。";
            case "DCF", "STAGE_DCF" -> "使用现金流折现，重点依赖 WACC、增长率和现金流质量假设。";
            case "PB", "PB_ROE_KE", "P_PPOP" -> "使用资产和回报率框架，观察 ROE 与账面价值支撑。";
            case "EV_EBITDA", "MID_CYCLE_EV_EBITDA", "MID_CYCLE_PROFIT" -> "适用于利润波动较大的企业，用中周期盈利能力平滑估值。";
            case "PS", "EV_SALES", "EV_ARR" -> "适合高成长或利润尚未稳定的企业，以收入和商业扩张估值。";
            case "NAV", "SOTP" -> "强调资产价值和分部价值，对控股和资产型公司更有效。";
            default -> "用于与其他模型交叉验证，避免单模型误差。";
        };
    }

    private String level(double factor, double highRiskThreshold, double mediumRiskThreshold) {
        if (factor < highRiskThreshold) {
            return "High";
        }
        if (factor < mediumRiskThreshold) {
            return "Medium";
        }
        return "Low";
    }

    private String pricePosition(double price, double fairLow, double fairMid, double fairHigh) {
        if (price <= fairLow) {
            return "低于安全边际区间";
        }
        if (price <= fairMid) {
            return "处于低估至合理区间";
        }
        if (price <= fairHigh) {
            return "处于合理至偏贵区间";
        }
        return "高于合理价值上沿（需谨慎）";
    }

    private String verdict(double upside) {
        if (upside >= 0.20) {
            return "显著低估（可重点跟踪）";
        }
        if (upside >= 0.05) {
            return "低估";
        }
        if (upside <= -0.20) {
            return "显著高估（注意回撤风险）";
        }
        if (upside <= -0.05) {
            return "偏高";
        }
        return "估值合理";
    }

    private String gradeLabel(int score) {
        if (score >= 82) {
            return "优秀";
        }
        if (score >= 68) {
            return "良好";
        }
        if (score >= 45) {
            return "估值合理";
        }
        return "差";
    }

    private String analystRating(double upside, int confidence) {
        if (upside >= 0.35 && confidence >= 75) {
            return "强力买入";
        }
        if (upside >= 0.15) {
            return "买入";
        }
        if (upside <= -0.15) {
            return "卖出";
        }
        return "中性";
    }

    private double marketCapInYi(Double marketCap, StockSnapshot snapshot) {
        if (marketCap != null && marketCap > 0) {
            return marketCap / 100_000_000.0;
        }
        return MathSupport.round(snapshot.price() * 10.0);
    }

    private record CnAdjustment(
            double policyFactor,
            double liquidityFactor,
            double governanceFactor,
            double structureFactor,
            double styleFactor
    ) {
        double totalFactor() {
            return policyFactor * liquidityFactor * governanceFactor * structureFactor * styleFactor;
        }
    }
}
