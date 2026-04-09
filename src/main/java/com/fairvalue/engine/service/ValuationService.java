package com.fairvalue.engine.service;

import com.fairvalue.engine.api.dto.ScenarioRequest;
import com.fairvalue.engine.api.dto.ScreenerCandidateItem;
import com.fairvalue.engine.api.dto.ScreenerFilterCatalogDefaults;
import com.fairvalue.engine.api.dto.ScreenerFilterCatalogResponse;
import com.fairvalue.engine.api.dto.ScreenerFilterCategory;
import com.fairvalue.engine.api.dto.ScreenerFilterDefinition;
import com.fairvalue.engine.api.dto.ScreenerFilterOption;
import com.fairvalue.engine.api.dto.ScreenerRequest;
import com.fairvalue.engine.api.dto.ScreenerResponse;
import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.domain.StockFundamentals;
import com.fairvalue.engine.domain.StockSnapshot;
import com.fairvalue.engine.domain.ValuationStatus;
import com.fairvalue.engine.repository.MarketPriceDailyRepository;
import com.fairvalue.engine.repository.ValuationLatestSnapshotRepository;
import com.fairvalue.engine.repository.ValuationRunsRepository;
import com.fairvalue.engine.valuation.ExplainResult;
import com.fairvalue.engine.valuation.HistoryPoint;
import com.fairvalue.engine.valuation.MarketAdjustment;
import com.fairvalue.engine.valuation.MarketComputation;
import com.fairvalue.engine.valuation.ModelValuation;
import com.fairvalue.engine.valuation.ScenarioPoint;
import com.fairvalue.engine.valuation.ScenarioResult;
import com.fairvalue.engine.valuation.ValuationResult;
import com.fairvalue.engine.us.UsMarketPriceDailyRecord;
import com.fairvalue.engine.us.UsSecurityMaster;
import com.fairvalue.engine.us.UsSecurityMasterService;
import com.fairvalue.engine.us.UsStoredValuationSnapshotRecord;
import com.fairvalue.engine.us.UsValuationReadService;
import com.fairvalue.engine.us.UsValuationRunHistoryRecord;
import com.fairvalue.engine.valuation.strategy.MarketStrategyRegistry;
import com.fairvalue.engine.valuation.strategy.MathSupport;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ValuationService {
    private static final List<String> SUPPORTED_SCREENER_MARKETS = List.of("US", "CN", "JP", "HK");

    private final MarketDataService marketDataService;
    private final MarketStrategyRegistry strategyRegistry;
    private final UsSecurityMasterService usSecurityMasterService;
    private final MarketPriceDailyRepository marketPriceDailyRepository;
    private final ValuationRunsRepository valuationRunsRepository;
    private final ValuationLatestSnapshotRepository valuationLatestSnapshotRepository;
    private final UsValuationReadService usValuationReadService;

    public ValuationService(
            MarketDataService marketDataService,
            MarketStrategyRegistry strategyRegistry,
            UsSecurityMasterService usSecurityMasterService,
            MarketPriceDailyRepository marketPriceDailyRepository,
            ValuationRunsRepository valuationRunsRepository,
            ValuationLatestSnapshotRepository valuationLatestSnapshotRepository,
            UsValuationReadService usValuationReadService
    ) {
        this.marketDataService = marketDataService;
        this.strategyRegistry = strategyRegistry;
        this.usSecurityMasterService = usSecurityMasterService;
        this.marketPriceDailyRepository = marketPriceDailyRepository;
        this.valuationRunsRepository = valuationRunsRepository;
        this.valuationLatestSnapshotRepository = valuationLatestSnapshotRepository;
        this.usValuationReadService = usValuationReadService;
    }

    public ValuationResult valuate(Market market, String symbol) {
        return runValuation(market, symbol).result();
    }

    public ValuationResult valuate(StockSnapshot snapshot) {
        return runValuation(snapshot).result();
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
        if (market == Market.US) {
            List<HistoryPoint> persisted = historyFromPersistedUsData(symbol, boundedDays);
            if (!persisted.isEmpty()) {
                return persisted;
            }
        }
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
                    MathSupport.round(deviation),
                    null,
                    null,
                    true
            ));
        }

        return points;
    }

    private List<HistoryPoint> historyFromPersistedUsData(String symbol, int boundedDays) {
        Long securityId = usSecurityMasterService.resolveSecurityId(symbol).orElse(null);
        if (securityId == null) {
            return List.of();
        }

        List<UsMarketPriceDailyRecord> priceHistory = marketPriceDailyRepository.findRecentHistoryBySecurityId(securityId, boundedDays);
        if (priceHistory.isEmpty()) {
            return List.of();
        }

        List<UsValuationRunHistoryRecord> valuationHistory = valuationRunsRepository.findRecentHistoryBySecurityId(
                securityId,
                Math.max(12, Math.min(180, boundedDays))
        );

        double fallbackFairValue = valuationHistory.isEmpty()
                ? valuationLatestSnapshotRepository.findByTicker(normalizeTicker(symbol))
                    .map(UsStoredValuationSnapshotRecord::fairValueMid)
                    .orElse(0.0)
                : valuationHistory.get(valuationHistory.size() - 1).fairValueMid();

        List<HistoryPoint> points = new ArrayList<>(priceHistory.size());
        UsValuationRunHistoryRecord currentRun = null;
        int valuationIndex = 0;
        for (UsMarketPriceDailyRecord priceRecord : priceHistory) {
            while (valuationIndex < valuationHistory.size()
                    && !valuationHistory.get(valuationIndex).valuationDate().isAfter(priceRecord.tradeDate())) {
                currentRun = valuationHistory.get(valuationIndex);
                valuationIndex += 1;
            }

            double fairValue = currentRun == null ? fallbackFairValue : currentRun.fairValueMid();
            double closePrice = priceRecord.close() == null ? 0.0 : priceRecord.close().doubleValue();
            if (fairValue <= 0.0 || closePrice <= 0.0) {
                continue;
            }
            points.add(new HistoryPoint(
                    priceRecord.tradeDate(),
                    MathSupport.round(closePrice),
                    MathSupport.round(fairValue),
                    MathSupport.round(closePrice / fairValue - 1.0),
                    currentRun == null ? null : currentRun.valuationRunDate(),
                    currentRun == null ? null : currentRun.runId(),
                    false
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

        List<ValuationResult> results = new ArrayList<>();
        if (markets.contains(Market.US)) {
            results.addAll(usScreener(minUndervalued, minRoe, maxPb, minDividend, requirePositiveFcf, limit));
        }

        results.addAll(marketDataService.listSnapshots(markets.stream().filter(market -> market != Market.US).toList()).stream()
                .filter(snapshot -> snapshot.fundamentals().roe() >= minRoe)
                .filter(snapshot -> snapshot.fundamentals().pb() <= maxPb)
                .filter(snapshot -> snapshot.fundamentals().dividendYield() >= minDividend)
                .filter(snapshot -> !requirePositiveFcf || snapshot.fundamentals().positiveFreeCashFlow())
                .map(this::valuate)
                .filter(valuation -> valuation.upside() >= minUndervalued)
                .toList());

        return results.stream()
                .sorted(Comparator.comparingDouble(ValuationResult::upside).reversed()
                        .thenComparing(Comparator.comparingDouble(ValuationResult::confidence).reversed()))
                .limit(limit)
                .toList();
    }

    public ScreenerResponse screenerResponse(ScreenerRequest request) {
        List<Market> markets = resolveMarkets(request.markets());
        ScreenerCriteria criteria = resolveScreenerCriteria(request);
        boolean commonStocksOnly = request.commonStocksOnly() == null || request.commonStocksOnly();
        int limit = request.limit() == null ? 30 : Math.min(request.limit(), 100);

        List<ScreenerCandidateItem> candidates = new ArrayList<>();
        if (markets.contains(Market.US)) {
            candidates.addAll(usScreenerCandidates(criteria, commonStocksOnly));
        }

        candidates.addAll(marketDataService.listSnapshots(markets.stream().filter(market -> market != Market.US).toList()).stream()
                .map(snapshot -> toScreenerCandidate(snapshot, valuate(snapshot), null, false))
                .filter(item -> matchesCandidate(item, criteria))
                .toList());

        List<ScreenerCandidateItem> sorted = candidates.stream()
                .sorted(screenerCandidateComparator(criteria.query()))
                .toList();
        int total = sorted.size();
        List<ScreenerCandidateItem> top = sorted.stream()
                .limit(limit)
                .toList();
        return new ScreenerResponse(total, top.size(), top);
    }

    public ScreenerFilterCatalogResponse screenerFilterCatalog() {
        List<UsSecurityMaster> usUniverse = usSecurityMasterService.findActiveUsUniverse();
        List<StockSnapshot> nonUsSnapshots = marketDataService.listSnapshots(List.of(Market.CN, Market.JP, Market.HK));

        List<ScreenerFilterOption> exchangeOptions = facetOptions(usUniverse, UsSecurityMaster::exchange, Function.identity());
        List<ScreenerFilterOption> sectorOptions = facetOptions(usUniverse, UsSecurityMaster::sector, Function.identity());
        List<ScreenerFilterOption> industryOptions = facetOptions(
                mergeValues(
                        usUniverse.stream().map(UsSecurityMaster::industry).toList(),
                        nonUsSnapshots.stream().map(StockSnapshot::industry).toList()
                ),
                Function.identity()
        );
        List<ScreenerFilterOption> companyTypeOptions = facetOptions(usUniverse, UsSecurityMaster::companyType, this::humanizeFacetLabel);
        List<ScreenerFilterOption> riskLevelOptions = List.of(
                new ScreenerFilterOption("LOW", "低风险", null),
                new ScreenerFilterOption("MEDIUM", "中风险", null),
                new ScreenerFilterOption("HIGH", "高风险", null)
        );

        ScreenerFilterCatalogDefaults defaults = new ScreenerFilterCatalogDefaults(
                List.of(Market.US.name()),
                "",
                5.0,
                8.0,
                false,
                12.0,
                0.0,
                0.0,
                null,
                35.0,
                false,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                30
        );

        return new ScreenerFilterCatalogResponse(
                SUPPORTED_SCREENER_MARKETS,
                defaults,
                List.of(
                        filterCategory("hot", "热门", "优先看常用硬筛选条件，美股先行覆盖最完整。", SUPPORTED_SCREENER_MARKETS, List.of(
                                toggleField("commonStocksOnly", "仅普通股", "默认排除 ETF、SPAC、权证和单位。", List.of("US"), "supported"),
                                toggleField("requireStoredValuation", "仅已落库估值", "优先显示已经完整落库的估值结果。", List.of("US"), "supported"),
                                toggleField("requirePositiveFcf", "仅正自由现金流", "过滤掉自由现金流为负的公司。", SUPPORTED_SCREENER_MARKETS, "supported"),
                                integerField("limit", "结果上限", "控制单次返回的结果数量。", "30", 1.0, 100.0, 1.0, SUPPORTED_SCREENER_MARKETS, "supported")
                        )),
                        filterCategory("price", "价格", "按股价区间控制候选池。", SUPPORTED_SCREENER_MARKETS, List.of(
                                numberField("minPrice", "最低股价", "最小最新价。", "0", 0.0, null, 0.01, "currency", SUPPORTED_SCREENER_MARKETS, "supported"),
                                numberField("maxPrice", "最高股价", "留空表示不设上限。", "不限", 0.0, null, 0.01, "currency", SUPPORTED_SCREENER_MARKETS, "supported")
                        )),
                        filterCategory("valuation", "估值", "基于合理股价、倍数和估值带筛选。", SUPPORTED_SCREENER_MARKETS, List.of(
                                percentField("minUndervalued", "最低低估幅度", "相对合理股价的最低安全边际。", "5", 0.0, 100.0, 0.5, SUPPORTED_SCREENER_MARKETS, "supported"),
                                percentField("minMarginOfSafety", "最低安全边际", "以更保守的估值缓冲过滤候选池。", "留空", -20.0, 100.0, 0.5, SUPPORTED_SCREENER_MARKETS, "supported"),
                                numberField("minPe", "最低 PE", "过滤掉过低估值异常值时使用。", "留空", 0.0, 100.0, 0.1, "multiple", SUPPORTED_SCREENER_MARKETS, "supported"),
                                numberField("maxPe", "最高 PE", "控制估值上限。", "留空", 0.0, 100.0, 0.1, "multiple", SUPPORTED_SCREENER_MARKETS, "supported"),
                                numberField("maxPb", "最高 PB", "控制账面倍数上限。", "12", 0.0, 100.0, 0.1, "multiple", SUPPORTED_SCREENER_MARKETS, "supported"),
                                numberField("minEvEbitda", "最低 EV/EBITDA", "用于剔除极端倍数。", "留空", 0.0, 100.0, 0.1, "multiple", SUPPORTED_SCREENER_MARKETS, "supported"),
                                numberField("maxEvEbitda", "最高 EV/EBITDA", "企业价值倍数上限。", "留空", 0.0, 100.0, 0.1, "multiple", SUPPORTED_SCREENER_MARKETS, "supported")
                        )),
                        filterCategory("insight", "洞察", "按置信度、覆盖度和流动性筛选。", SUPPORTED_SCREENER_MARKETS, List.of(
                                percentField("minConfidence", "最低置信度", "估值模型综合置信分。", "35", 0.0, 100.0, 1.0, SUPPORTED_SCREENER_MARKETS, "supported"),
                                percentField("minAnalystCoverage", "最低分析师覆盖度", "越高表示外部研究覆盖越充分。", "留空", 0.0, 100.0, 1.0, SUPPORTED_SCREENER_MARKETS, "supported"),
                                percentField("minLiquidityScore", "最低流动性得分", "越高表示成交与规模越充足。", "留空", 0.0, 100.0, 1.0, SUPPORTED_SCREENER_MARKETS, "supported"),
                                percentField("analystTargetMargin", "分析师目标价上行边际", "预留给后续接入一致预期目标价。", "即将支持", null, null, 1.0, List.of("US"), "coming_soon")
                        )),
                        filterCategory("finance", "财务", "按资产质量和数据完整度筛选。", SUPPORTED_SCREENER_MARKETS, List.of(
                                percentField("minNetCashToMarketCap", "最低净现金/市值", "净现金占市值的最低比例。", "留空", -100.0, 100.0, 1.0, SUPPORTED_SCREENER_MARKETS, "supported"),
                                percentField("minDataCompleteness", "最低数据完整度", "衡量估值输入项覆盖率。", "留空", 0.0, 100.0, 1.0, SUPPORTED_SCREENER_MARKETS, "supported"),
                                numberField("maxDataFreshnessDays", "最大数据滞后天数", "越小表示数据越新。", "留空", 0.0, 365.0, 1.0, "days", SUPPORTED_SCREENER_MARKETS, "supported")
                        )),
                        filterCategory("quality", "质量", "区分便宜但脆弱，还是便宜且有基本面支撑。", SUPPORTED_SCREENER_MARKETS, List.of(
                                percentField("minQualityScore", "最低质量评分", "综合盈利、现金流、治理和数据可靠性。", "留空", 0.0, 100.0, 1.0, SUPPORTED_SCREENER_MARKETS, "supported")
                        )),
                        filterCategory("dividend", "股息", "关注分红与回购回报。", SUPPORTED_SCREENER_MARKETS, List.of(
                                percentField("minDividendYield", "最低股息率", "现金股息收益率下限。", "0", 0.0, 30.0, 0.1, SUPPORTED_SCREENER_MARKETS, "supported"),
                                percentField("minBuybackYield", "最低回购收益率", "回购收益率下限。", "留空", 0.0, 30.0, 0.1, SUPPORTED_SCREENER_MARKETS, "supported"),
                                integerField("dividendStreakYears", "股息连续派发纪录", "预留给后续股息历史序列。", "即将支持", 0.0, 100.0, 1.0, SUPPORTED_SCREENER_MARKETS, "coming_soon")
                        )),
                        filterCategory("growth", "增长", "按营收增速筛选成长性。", SUPPORTED_SCREENER_MARKETS, List.of(
                                percentField("minRevenueGrowth", "最低营收增速", "最近周期收入增速下限。", "留空", -50.0, 100.0, 1.0, SUPPORTED_SCREENER_MARKETS, "supported")
                        )),
                        filterCategory("return", "回报", "优先看股东回报能力。", SUPPORTED_SCREENER_MARKETS, List.of(
                                percentField("minRoe", "最低 ROE", "净资产收益率下限。", "8", 0.0, 100.0, 0.5, SUPPORTED_SCREENER_MARKETS, "supported")
                        )),
                        filterCategory("risk", "风险", "控制波动和脆弱性。", SUPPORTED_SCREENER_MARKETS, List.of(
                                percentField("maxEarningsVolatility", "最大波动度", "越低表示盈利波动越小。", "留空", 0.0, 100.0, 1.0, SUPPORTED_SCREENER_MARKETS, "supported"),
                                multiSelectField("riskLevels", "风险等级", "优先控制研究顺序，先排除高风险候选。", SUPPORTED_SCREENER_MARKETS, "supported", riskLevelOptions),
                                toggleField("excludeValueTrap", "排除价值陷阱", "过滤掉便宜但质量或风险结构异常的候选。", SUPPORTED_SCREENER_MARKETS, "supported")
                        )),
                        filterCategory("technical", "技术", "技术指标位于样式层预留，等待行情序列补齐。", SUPPORTED_SCREENER_MARKETS, List.of(
                                numberField("rsi14", "RSI(14)", "后续接入日线技术序列。", "即将支持", 0.0, 100.0, 1.0, "score", SUPPORTED_SCREENER_MARKETS, "coming_soon"),
                                percentField("ytdPriceChange", "今年迄今价格变动", "后续接入复权价格序列。", "即将支持", -100.0, 300.0, 1.0, SUPPORTED_SCREENER_MARKETS, "coming_soon"),
                                percentField("dailyPriceChange", "日涨跌幅", "后续接入日内快照序列。", "即将支持", -30.0, 30.0, 0.1, SUPPORTED_SCREENER_MARKETS, "coming_soon")
                        )),
                        filterCategory("efficiency", "效率", "控制模型输入效率和资产利用率。", SUPPORTED_SCREENER_MARKETS, List.of(
                                numberField("indexMembership", "指数", "预留给指数成分与主题标签。", "即将支持", null, null, 1.0, null, SUPPORTED_SCREENER_MARKETS, "coming_soon")
                        )),
                        filterCategory("profile", "简介", "按交易所、行业和公司类型缩小范围。", SUPPORTED_SCREENER_MARKETS, List.of(
                                multiSelectField("exchanges", "交易所", "美股交易所元数据已接通。", List.of("US"), "supported", exchangeOptions),
                                multiSelectField("sectors", "板块", "基于美股 security master 板块标签。", List.of("US"), "supported", sectorOptions),
                                multiSelectField("industries", "行业", "支持按行业精确过滤。", SUPPORTED_SCREENER_MARKETS, "supported", industryOptions),
                                multiSelectField("companyTypes", "公司类型", "如 compounder、cyclical、asset_light。", List.of("US"), "supported", companyTypeOptions)
                        ))
                )
        );
    }

    private List<ValuationResult> usScreener(
            double minUndervalued,
            double minRoe,
            double maxPb,
            double minDividend,
            boolean requirePositiveFcf,
            int limit
    ) {
        List<StockSnapshot> snapshots = marketDataService.listSnapshots(List.of(Market.US));
        Map<String, UsStoredValuationSnapshotRecord> storedByTicker = valuationLatestSnapshotRepository.findAllByMarket(Market.US.name()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        snapshot -> normalizeTicker(snapshot.ticker()),
                        snapshot -> snapshot,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<String, StockSnapshot> snapshotByTicker = snapshots.stream()
                .collect(java.util.stream.Collectors.toMap(
                        snapshot -> normalizeTicker(snapshot.symbol()),
                        snapshot -> snapshot,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        return snapshots.stream()
                .filter(snapshot -> snapshot.fundamentals().roe() >= minRoe)
                .filter(snapshot -> snapshot.fundamentals().pb() <= maxPb)
                .filter(snapshot -> snapshot.fundamentals().dividendYield() >= minDividend)
                .filter(snapshot -> !requirePositiveFcf || snapshot.fundamentals().positiveFreeCashFlow())
                .map(snapshot -> {
                    UsStoredValuationSnapshotRecord stored = storedByTicker.get(normalizeTicker(snapshot.symbol()));
                    return stored == null
                            ? quickUsSnapshotValuation(snapshot)
                            : usValuationReadService.toStoredValuationResult(snapshot, stored);
                })
                .filter(valuation -> valuation.upside() >= minUndervalued)
                .sorted(Comparator.comparingDouble(ValuationResult::upside).reversed()
                        .thenComparing(Comparator.comparingDouble(ValuationResult::confidence).reversed()))
                .limit(limit)
                .toList();
    }

    private List<ScreenerCandidateItem> usScreenerCandidates(ScreenerCriteria criteria, boolean commonStocksOnly) {
        Map<String, UsStoredValuationSnapshotRecord> storedByTicker = valuationLatestSnapshotRepository.findAllByMarket(Market.US.name()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        snapshot -> normalizeTicker(snapshot.ticker()),
                        snapshot -> snapshot,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        return usSecurityMasterService.findActiveUsUniverse().stream()
                .filter(security -> !commonStocksOnly || isPrimaryCommonStock(security))
                .filter(security -> matchesSecurityMetadata(security, criteria))
                .map(security -> {
                    StockSnapshot snapshot = buildUsScreenerSnapshot(security);
                    UsStoredValuationSnapshotRecord stored = storedByTicker.get(normalizeTicker(security.ticker()));
                    if (stored == null && isSyntheticSnapshot(snapshot)) {
                        return null;
                    }
                    ValuationResult valuation = stored == null
                            ? quickUsSnapshotValuation(snapshot)
                            : usValuationReadService.toStoredValuationResult(snapshot, stored);
                    return toScreenerCandidate(snapshot, valuation, security, stored != null);
                })
                .filter(java.util.Objects::nonNull)
                .filter(item -> matchesCandidate(item, criteria))
                .toList();
    }

    private EngineOutput runValuation(Market market, String symbol) {
        StockSnapshot snapshot = marketDataService.getSnapshot(market, symbol);
        return runValuation(snapshot);
    }

    private EngineOutput runValuation(StockSnapshot snapshot) {
        MarketComputation computation = strategyRegistry.get(snapshot.market()).evaluate(snapshot);

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
                snapshot.market(),
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

    private ValuationResult quickUsSnapshotValuation(StockSnapshot snapshot) {
        StockFundamentals fundamentals = snapshot.fundamentals();
        double qualityMultiplier = MathSupport.clamp(
                0.92
                        + fundamentals.revenueGrowth() * 0.55
                        + fundamentals.fcfMargin() * 0.60
                        + fundamentals.roe() * 0.35
                        + fundamentals.netCashToMarketCap() * 0.18
                        + fundamentals.themePremium() * 0.10
                        - fundamentals.wacc() * 0.95
                        - fundamentals.earningsVolatility() * 0.32,
                0.68,
                1.58
        );
        double terminalSupport = MathSupport.clamp(
                1.0 + fundamentals.terminalGrowth() * 3.2 + fundamentals.dividendYield() * 0.45,
                0.92,
                1.18
        );
        double fairValueMid = snapshot.price() * qualityMultiplier * terminalSupport;
        double confidence = MathSupport.clamp(
                fundamentals.dataCompleteness() * 0.35
                        + MathSupport.clamp(1.0 - fundamentals.dataFreshnessDays() / 60.0, 0.20, 1.0) * 0.20
                        + fundamentals.liquidityScore() * 0.15
                        + fundamentals.analystCoverage() * 0.15
                        + fundamentals.governanceScore() * 0.15,
                0.25,
                0.95
        );
        double band = MathSupport.clamp(
                0.10 + (1.0 - confidence) * 0.18 + fundamentals.earningsVolatility() * 0.10,
                0.10,
                0.34
        );
        double fairValueLow = fairValueMid * (1.0 - band);
        double fairValueHigh = fairValueMid * (1.0 + band);
        double upside = fairValueMid / Math.max(snapshot.price(), 0.1) - 1.0;
        Map<String, Double> diagnosticDrivers = new LinkedHashMap<>();
        diagnosticDrivers.put("confidence_level", MathSupport.round(confidence));
        diagnosticDrivers.put("quality_multiplier", MathSupport.round(qualityMultiplier));
        diagnosticDrivers.put("valuation_band", MathSupport.round(band));

        return new ValuationResult(
                snapshot.market(),
                snapshot.symbol(),
                snapshot.currency(),
                MathSupport.round(snapshot.price()),
                LocalDate.now(),
                MathSupport.round(fairValueMid),
                MathSupport.round(fairValueMid),
                MathSupport.round(fairValueLow),
                MathSupport.round(fairValueHigh),
                MathSupport.round(upside),
                MathSupport.round(confidence),
                ValuationStatus.fromUpside(upside),
                List.of(),
                Map.copyOf(diagnosticDrivers),
                List.of(),
                quickUsDiagnosticFlags(snapshot, confidence),
                snapshot.dataVersion()
        );
    }

    private StockSnapshot buildUsScreenerSnapshot(UsSecurityMaster security) {
        StockSnapshot base = marketDataService.getCachedOrSyntheticSnapshot(Market.US, security.ticker());
        return new StockSnapshot(
                Market.US,
                security.ticker(),
                firstNonBlank(security.currency(), base.currency(), "USD"),
                firstNonBlank(security.companyName(), base.companyName(), security.ticker()),
                firstNonBlank(security.industry(), security.sector(), base.industry(), "General"),
                base.price(),
                base.fundamentals(),
                base.dataVersion()
        );
    }

    private ScreenerCandidateItem toScreenerCandidate(
            StockSnapshot snapshot,
            ValuationResult valuation,
            UsSecurityMaster security,
            boolean storedValuation
    ) {
        StockFundamentals fundamentals = snapshot.fundamentals();
        ScreenerDecisionMetrics decisionMetrics = resolveScreenerDecisionMetrics(snapshot, valuation);
        boolean syntheticCandidate = !storedValuation && isSyntheticSnapshot(snapshot);
        return new ScreenerCandidateItem(
                snapshot.market(),
                snapshot.symbol(),
                snapshot.currency(),
                firstNonBlank(security == null ? null : security.companyName(), snapshot.companyName()),
                security == null ? null : security.exchange(),
                security == null ? null : security.sector(),
                firstNonBlank(security == null ? null : security.industry(), snapshot.industry()),
                security == null ? null : security.companyType(),
                security == null ? null : security.sectorTemplate(),
                null,
                roundDisplayMetric(fundamentals.pe()),
                roundDisplayMetric(fundamentals.pb()),
                roundDisplayMetric(fundamentals.evEbitda()),
                roundDisplayMetric(fundamentals.roe()),
                roundDisplayMetric(fundamentals.dividendYield()),
                roundDisplayMetric(fundamentals.buybackYield()),
                roundDisplayMetric(fundamentals.revenueGrowth()),
                roundDisplayMetric(fundamentals.netCashToMarketCap()),
                roundDisplayMetric(fundamentals.analystCoverage()),
                roundDisplayMetric(fundamentals.liquidityScore()),
                roundDisplayMetric(fundamentals.earningsVolatility()),
                roundDisplayMetric(fundamentals.dataFreshnessDays()),
                roundDisplayMetric(fundamentals.dataCompleteness()),
                roundDisplayMetric(fundamentals.governanceScore()),
                fundamentals.positiveFreeCashFlow(),
                valuation.price(),
                valuation.tradableFairValue(),
                valuation.fairValueLow(),
                valuation.fairValueHigh(),
                valuation.upside(),
                valuation.confidence(),
                valuation.valuationStatus(),
                decisionMetrics.marginOfSafety(),
                decisionMetrics.qualityScore(),
                decisionMetrics.riskLevel(),
                decisionMetrics.valueTrapFlag(),
                mergeScreenerDrivers(valuation, decisionMetrics),
                mergeScreenerRiskFlags(valuation, decisionMetrics),
                valuation.dataVersion(),
                storedValuation,
                syntheticCandidate
        );
    }

    private ScreenerDecisionMetrics resolveScreenerDecisionMetrics(StockSnapshot snapshot, ValuationResult valuation) {
        StockFundamentals fundamentals = snapshot.fundamentals();
        Double marginOfSafety = extractDriverValue(valuation, "margin_of_safety");
        if (marginOfSafety == null) {
            marginOfSafety = estimateMarginOfSafety(valuation);
        }

        Double qualityScore = extractDriverValue(valuation, "quality_score");
        if (qualityScore == null) {
            qualityScore = estimateQualityScore(fundamentals, valuation.confidence());
        }
        qualityScore = clampFiniteMetric(qualityScore, 0.0, 1.0);

        boolean valueTrapFlag = resolveValueTrapFlag(valuation, fundamentals, marginOfSafety, qualityScore);
        double riskScore = resolveRiskScore(valuation, fundamentals, marginOfSafety, qualityScore, valueTrapFlag);

        return new ScreenerDecisionMetrics(
                roundFiniteMetric(marginOfSafety),
                roundFiniteMetric(qualityScore),
                riskLevelFromScore(riskScore),
                valueTrapFlag,
                MathSupport.round(riskScore)
        );
    }

    private Double estimateMarginOfSafety(ValuationResult valuation) {
        if (valuation == null || valuation.tradableFairValue() <= 0.0 || valuation.price() <= 0.0) {
            return null;
        }
        return MathSupport.clamp(
                (valuation.tradableFairValue() - valuation.price()) / valuation.tradableFairValue(),
                -0.85,
                0.95
        );
    }

    private Double estimateQualityScore(StockFundamentals fundamentals, double confidence) {
        if (fundamentals == null) {
            return clampFiniteMetric(confidence, 0.20, 0.95);
        }

        double profitability = scaleRatio(Math.max(fundamentals.roe(), fundamentals.fcfMargin()), -0.02, 0.25);
        double growth = scaleRatio(fundamentals.revenueGrowth(), -0.05, 0.20);
        double stability = 1.0 - normalize01(fundamentals.earningsVolatility());
        double governance = normalize01(fundamentals.governanceScore());
        double completeness = normalize01(fundamentals.dataCompleteness());
        double liquidity = normalize01(fundamentals.liquidityScore());
        double cashConversion = fundamentals.positiveFreeCashFlow() ? 1.0 : 0.35;
        double confidenceScore = normalize01(confidence);

        return MathSupport.clamp(
                profitability * 0.28
                        + growth * 0.10
                        + stability * 0.16
                        + governance * 0.14
                        + completeness * 0.12
                        + liquidity * 0.08
                        + cashConversion * 0.07
                        + confidenceScore * 0.05,
                0.25,
                0.95
        );
    }

    private boolean resolveValueTrapFlag(
            ValuationResult valuation,
            StockFundamentals fundamentals,
            Double marginOfSafety,
            Double qualityScore
    ) {
        if (hasRiskFlag(valuation, "value_trap")) {
            return true;
        }
        if (fundamentals == null) {
            return false;
        }

        boolean looksCheap = valueOrFloor(marginOfSafety, Double.NEGATIVE_INFINITY) >= 0.08
                || valuation.upside() >= 0.12;
        boolean weakQuality = valueOrFloor(qualityScore, 1.0) < 0.45;
        boolean fragileBusiness = !fundamentals.positiveFreeCashFlow()
                || fundamentals.earningsVolatility() >= 0.45
                || fundamentals.governanceScore() < 0.45;
        boolean poorDataSupport = fundamentals.dataCompleteness() < 0.55
                || fundamentals.dataFreshnessDays() > 120.0
                || valuation.confidence() < 0.45;

        return looksCheap && weakQuality && (fragileBusiness || poorDataSupport);
    }

    private double resolveRiskScore(
            ValuationResult valuation,
            StockFundamentals fundamentals,
            Double marginOfSafety,
            Double qualityScore,
            boolean valueTrapFlag
    ) {
        if (fundamentals == null) {
            return MathSupport.clamp((1.0 - normalize01(valuation.confidence())) + (valueTrapFlag ? 0.20 : 0.0), 0.20, 0.95);
        }

        double riskScore =
                normalize01(fundamentals.earningsVolatility()) * 0.24
                        + (1.0 - normalize01(fundamentals.governanceScore())) * 0.14
                        + (1.0 - normalize01(fundamentals.dataCompleteness())) * 0.12
                        + normalizeFreshnessRisk(fundamentals.dataFreshnessDays()) * 0.10
                        + (fundamentals.positiveFreeCashFlow() ? 0.0 : 0.10)
                        + (1.0 - normalize01(fundamentals.liquidityScore())) * 0.08
                        + (1.0 - normalize01(valuation.confidence())) * 0.08
                        + lowMarginRisk(marginOfSafety) * 0.08
                        + lowQualityRisk(qualityScore) * 0.08
                        + riskFlagPenalty(valuation.riskFlags()) * 0.12
                        + (valueTrapFlag ? 0.16 : 0.0);

        return MathSupport.clamp(riskScore, 0.0, 1.0);
    }

    private double lowMarginRisk(Double marginOfSafety) {
        double value = valueOrFloor(marginOfSafety, 0.0);
        if (value >= 0.15) {
            return 0.0;
        }
        if (value >= 0.05) {
            return 0.35;
        }
        if (value >= 0.0) {
            return 0.65;
        }
        return 1.0;
    }

    private double lowQualityRisk(Double qualityScore) {
        double value = valueOrFloor(qualityScore, 0.0);
        if (value >= 0.70) {
            return 0.0;
        }
        if (value >= 0.55) {
            return 0.35;
        }
        if (value >= 0.45) {
            return 0.70;
        }
        return 1.0;
    }

    private double normalizeFreshnessRisk(double dataFreshnessDays) {
        if (!Double.isFinite(dataFreshnessDays) || dataFreshnessDays <= 0.0) {
            return 0.0;
        }
        return MathSupport.clamp(dataFreshnessDays / 180.0, 0.0, 1.0);
    }

    private double riskFlagPenalty(List<String> riskFlags) {
        if (riskFlags == null || riskFlags.isEmpty()) {
            return 0.0;
        }
        double penalty = 0.0;
        for (String flag : riskFlags) {
            String normalized = normalizeFlagToken(flag);
            if ("value_trap".equals(normalized) || "not_rankable".equals(normalized)) {
                penalty += 0.32;
            } else if ("data_quality_low".equals(normalized) || "confidence_low".equals(normalized)) {
                penalty += 0.22;
            } else if ("price_stale".equals(normalized)
                    || "quick_snapshot_only".equals(normalized)
                    || normalized.contains("manual_review")
                    || normalized.contains("missing")
                    || normalized.contains("not_structured")) {
                penalty += 0.15;
            } else if (normalized.contains("template")
                    || normalized.contains("fallback")
                    || normalized.contains("profile")
                    || normalized.contains("industry")) {
                penalty += 0.08;
            } else if (normalized.contains("warn") || normalized.endsWith("_medium")) {
                penalty += 0.05;
            }
        }
        return MathSupport.clamp(penalty, 0.0, 1.0);
    }

    private String riskLevelFromScore(double riskScore) {
        if (riskScore >= 0.62) {
            return "HIGH";
        }
        if (riskScore >= 0.36) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private Map<String, Double> mergeScreenerDrivers(ValuationResult valuation, ScreenerDecisionMetrics decisionMetrics) {
        Map<String, Double> merged = new LinkedHashMap<>();
        if (valuation.drivers() != null) {
            merged.putAll(valuation.drivers());
        }
        if (decisionMetrics.marginOfSafety() != null) {
            merged.put("margin_of_safety", decisionMetrics.marginOfSafety());
        }
        if (decisionMetrics.qualityScore() != null) {
            merged.put("quality_score", decisionMetrics.qualityScore());
        }
        if (decisionMetrics.riskScore() != null) {
            merged.put("risk_score", decisionMetrics.riskScore());
        }
        return Map.copyOf(merged);
    }

    private List<String> mergeScreenerRiskFlags(ValuationResult valuation, ScreenerDecisionMetrics decisionMetrics) {
        List<String> merged = new ArrayList<>();
        if (valuation.riskFlags() != null) {
            merged.addAll(valuation.riskFlags());
        }
        if (Boolean.TRUE.equals(decisionMetrics.valueTrapFlag())) {
            merged.add("value_trap");
        }
        if (decisionMetrics.riskLevel() != null && !decisionMetrics.riskLevel().isBlank()) {
            merged.add("risk_level_" + decisionMetrics.riskLevel().toLowerCase(Locale.ROOT));
        }
        return merged.stream()
                .filter(flag -> flag != null && !flag.isBlank())
                .distinct()
                .toList();
    }

    private Double extractDriverValue(ValuationResult valuation, String key) {
        if (valuation == null || valuation.drivers() == null || key == null || key.isBlank()) {
            return null;
        }
        Double value = valuation.drivers().get(key);
        return value == null || !Double.isFinite(value) ? null : value;
    }

    private boolean hasRiskFlag(ValuationResult valuation, String expectedFlag) {
        if (valuation == null || valuation.riskFlags() == null || expectedFlag == null || expectedFlag.isBlank()) {
            return false;
        }
        String normalizedExpected = normalizeFlagToken(expectedFlag);
        return valuation.riskFlags().stream()
                .map(this::normalizeFlagToken)
                .anyMatch(normalizedExpected::equals);
    }

    private Double clampFiniteMetric(Double value, double min, double max) {
        if (value == null || !Double.isFinite(value)) {
            return null;
        }
        return MathSupport.clamp(value, min, max);
    }

    private Double roundFiniteMetric(Double value) {
        return value == null || !Double.isFinite(value) ? null : MathSupport.round(value);
    }

    private double normalize01(double rawValue) {
        if (!Double.isFinite(rawValue)) {
            return 0.0;
        }
        return MathSupport.clamp(rawValue, 0.0, 1.0);
    }

    private double scaleRatio(double rawValue, double minAnchor, double maxAnchor) {
        if (!Double.isFinite(rawValue)) {
            return 0.0;
        }
        if (maxAnchor <= minAnchor) {
            return normalize01(rawValue);
        }
        return MathSupport.clamp((rawValue - minAnchor) / (maxAnchor - minAnchor), 0.0, 1.0);
    }

    private boolean matchesCandidate(ScreenerCandidateItem item, ScreenerCriteria criteria) {
        if (!matchesQuery(criteria.query(), item.symbol(), item.companyName(), item.sector(), item.industry())) {
            return false;
        }
        if (!matchesFacet(criteria.exchanges(), item.exchange())) {
            return false;
        }
        if (!matchesFacet(criteria.sectors(), item.sector())) {
            return false;
        }
        if (!matchesFacet(criteria.industries(), item.industry())) {
            return false;
        }
        if (!matchesFacet(criteria.companyTypes(), item.companyType())) {
            return false;
        }
        if (valueOrFloor(item.upside(), -1.0) < criteria.minUndervalued()) {
            return false;
        }
        if (criteria.minRoe() > 0.0 && valueOrFloor(item.roe(), Double.NEGATIVE_INFINITY) < criteria.minRoe()) {
            return false;
        }
        if (valueOrCeiling(item.pb(), Double.MAX_VALUE) > criteria.maxPb()) {
            return false;
        }
        if (criteria.minDividendYield() > 0.0
                && valueOrFloor(item.dividendYield(), Double.NEGATIVE_INFINITY) < criteria.minDividendYield()) {
            return false;
        }
        if (valueOrFloor(item.price(), Double.NEGATIVE_INFINITY) < criteria.minPrice()) {
            return false;
        }
        if (valueOrCeiling(item.price(), Double.MAX_VALUE) > criteria.maxPrice()) {
            return false;
        }
        if (criteria.minConfidence() > 0.0 && valueOrFloor(item.confidence(), Double.NEGATIVE_INFINITY) < criteria.minConfidence()) {
            return false;
        }
        if (criteria.requirePositiveFcf() && !Boolean.TRUE.equals(item.positiveFreeCashFlow())) {
            return false;
        }
        if (criteria.requireStoredValuation() && !Boolean.TRUE.equals(item.storedValuation())) {
            return false;
        }
        if (criteria.minMarginOfSafety() != Double.NEGATIVE_INFINITY
                && valueOrFloor(item.marginOfSafety(), Double.NEGATIVE_INFINITY) < criteria.minMarginOfSafety()) {
            return false;
        }
        if (criteria.minQualityScore() > 0.0
                && valueOrFloor(item.qualityScore(), Double.NEGATIVE_INFINITY) < criteria.minQualityScore()) {
            return false;
        }
        if (!matchesFacet(criteria.riskLevels(), item.riskLevel())) {
            return false;
        }
        if (criteria.excludeValueTrap() && Boolean.TRUE.equals(item.valueTrapFlag())) {
            return false;
        }
        if (criteria.minPe() > 0.0 && valueOrFloor(item.pe(), Double.NEGATIVE_INFINITY) < criteria.minPe()) {
            return false;
        }
        if (valueOrCeiling(item.pe(), Double.MAX_VALUE) > criteria.maxPe()) {
            return false;
        }
        if (criteria.minEvEbitda() > 0.0 && valueOrFloor(item.evEbitda(), Double.NEGATIVE_INFINITY) < criteria.minEvEbitda()) {
            return false;
        }
        if (valueOrCeiling(item.evEbitda(), Double.MAX_VALUE) > criteria.maxEvEbitda()) {
            return false;
        }
        if (criteria.minRevenueGrowth() != Double.NEGATIVE_INFINITY
                && valueOrFloor(item.revenueGrowth(), Double.NEGATIVE_INFINITY) < criteria.minRevenueGrowth()) {
            return false;
        }
        if (criteria.minBuybackYield() > 0.0
                && valueOrFloor(item.buybackYield(), Double.NEGATIVE_INFINITY) < criteria.minBuybackYield()) {
            return false;
        }
        if (criteria.minNetCashToMarketCap() != Double.NEGATIVE_INFINITY
                && valueOrFloor(item.netCashToMarketCap(), Double.NEGATIVE_INFINITY) < criteria.minNetCashToMarketCap()) {
            return false;
        }
        if (criteria.minAnalystCoverage() > 0.0
                && valueOrFloor(item.analystCoverage(), Double.NEGATIVE_INFINITY) < criteria.minAnalystCoverage()) {
            return false;
        }
        if (criteria.minLiquidityScore() > 0.0
                && valueOrFloor(item.liquidityScore(), Double.NEGATIVE_INFINITY) < criteria.minLiquidityScore()) {
            return false;
        }
        if (valueOrCeiling(item.earningsVolatility(), Double.MAX_VALUE) > criteria.maxEarningsVolatility()) {
            return false;
        }
        if (valueOrCeiling(item.dataFreshnessDays(), Double.MAX_VALUE) > criteria.maxDataFreshnessDays()) {
            return false;
        }
        return valueOrFloor(item.dataCompleteness(), Double.NEGATIVE_INFINITY) >= criteria.minDataCompleteness();
    }

    private Comparator<ScreenerCandidateItem> screenerCandidateComparator(String query) {
        return Comparator.comparingInt((ScreenerCandidateItem item) ->
                        queryMatchScore(query, item.symbol(), item.companyName(), item.sector(), item.industry()))
                .reversed()
                .thenComparing(Comparator.comparingDouble((ScreenerCandidateItem item) -> valueOrFloor(item.upside(), Double.NEGATIVE_INFINITY)).reversed())
                .thenComparing(Comparator.comparingDouble((ScreenerCandidateItem item) -> valueOrFloor(item.confidence(), Double.NEGATIVE_INFINITY)).reversed())
                .thenComparing(item -> Boolean.TRUE.equals(item.storedValuation()) ? 0 : 1)
                .thenComparing(ScreenerCandidateItem::symbol);
    }

    private int queryMatchScore(String query, String symbol, String companyName, String sector, String industry) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isBlank()) {
            return 0;
        }
        return Math.max(
                scoreTickerMatch(normalizedQuery, symbol),
                Math.max(
                        scoreTextMatch(normalizedQuery, companyName, 600),
                        Math.max(
                                scoreTextMatch(normalizedQuery, sector, 260),
                                scoreTextMatch(normalizedQuery, industry, 320)
                        )
                )
        );
    }

    private int scoreTickerMatch(String query, String rawTicker) {
        String ticker = normalizeTickerForSearch(rawTicker);
        String normalizedQuery = normalizeTickerForSearch(query);
        if (ticker.isBlank() || normalizedQuery.isBlank()) {
            return 0;
        }
        if (ticker.equals(normalizedQuery)) {
            return 1000;
        }
        if (ticker.startsWith(normalizedQuery)) {
            return 900;
        }
        return ticker.contains(normalizedQuery) ? 750 : 0;
    }

    private int scoreTextMatch(String query, String rawValue, int baseScore) {
        String value = normalizeSearchText(rawValue);
        if (value.isBlank()) {
            return 0;
        }
        if (value.equals(query)) {
            return baseScore;
        }
        if (value.startsWith(query)) {
            return baseScore - 30;
        }
        if (matchesTokenPrefixSequence(value, query)) {
            return baseScore - 60;
        }
        return containsQueryOnWordBoundary(value, query) ? baseScore - 120 : 0;
    }

    private boolean matchesTokenPrefixSequence(String normalizedValue, String normalizedQuery) {
        String[] valueTokens = normalizedValue.split("\\s+");
        String[] queryTokens = normalizedQuery.split("\\s+");
        if (valueTokens.length == 0 || queryTokens.length == 0 || valueTokens.length < queryTokens.length) {
            return false;
        }
        for (int start = 0; start <= valueTokens.length - queryTokens.length; start++) {
            boolean allMatch = true;
            for (int index = 0; index < queryTokens.length; index++) {
                if (!valueTokens[start + index].startsWith(queryTokens[index])) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                return true;
            }
        }
        return false;
    }

    private boolean containsQueryOnWordBoundary(String normalizedValue, String normalizedQuery) {
        return (" " + normalizedValue + " ").contains(" " + normalizedQuery + " ");
    }

    private String normalizeTickerForSearch(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "";
        }
        return rawValue.trim()
                .toUpperCase(Locale.ROOT)
                .replace(".US", "")
                .replaceAll("[^A-Z0-9]+", "");
    }

    private boolean isPrimaryCommonStock(UsSecurityMaster security) {
        String ticker = firstNonBlank(security.ticker(), "").toUpperCase(Locale.ROOT);
        String symbolFull = firstNonBlank(security.symbolFull(), "").toUpperCase(Locale.ROOT);
        if (looksLikeSpecialUsSecurity(ticker, symbolFull)) {
            return false;
        }
        String companyName = normalizeSearchText(firstNonBlank(security.companyName(), ""));
        if (companyName.isBlank()) {
            return true;
        }
        return !containsAny(companyName,
                "etf",
                "exchange traded fund",
                "trust",
                "fund",
                "warrant",
                "rights",
                "rights offering",
                "unit",
                "units",
                "acquisition",
                "blank check",
                "preferred",
                "depositary shares",
                "physical gold"
        );
    }

    private boolean looksLikeSpecialUsSecurity(String ticker, String symbolFull) {
        if (containsAny(ticker, "-W", "-WS", "-WT", "-U", "-UN", "-R", "-RT", "-WI")) {
            return true;
        }
        if (containsAny(symbolFull, ".WS.", ".WT.", ".UN.", ".RT.")) {
            return true;
        }
        if (ticker.length() >= 5) {
            return ticker.endsWith("W")
                    || ticker.endsWith("WS")
                    || ticker.endsWith("WT")
                    || ticker.endsWith("U")
                    || ticker.endsWith("UN")
                    || ticker.endsWith("R")
                    || ticker.endsWith("RT");
        }
        return false;
    }

    private boolean matchesQuery(String query, String symbol, String companyName, String sector, String industry) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isBlank()) {
            return true;
        }
        return queryMatchScore(normalizedQuery, symbol, companyName, sector, industry) > 0;
    }

    private boolean matchesSecurityMetadata(UsSecurityMaster security, ScreenerCriteria criteria) {
        return matchesQuery(criteria.query(), security.ticker(), security.companyName(), security.sector(), security.industry())
                && matchesFacet(criteria.exchanges(), security.exchange())
                && matchesFacet(criteria.sectors(), security.sector())
                && matchesFacet(criteria.industries(), security.industry())
                && matchesFacet(criteria.companyTypes(), security.companyType());
    }

    private boolean matchesFacet(Set<String> selectedValues, String candidateValue) {
        if (selectedValues == null || selectedValues.isEmpty()) {
            return true;
        }
        if (candidateValue == null || candidateValue.isBlank()) {
            return false;
        }
        return selectedValues.contains(normalizeSearchText(candidateValue));
    }

    private boolean containsAny(String haystack, String... needles) {
        if (haystack == null || haystack.isBlank() || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeQuery(String rawQuery) {
        return normalizeSearchText(rawQuery);
    }

    private Set<String> normalizeFacetSelections(List<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return Set.of();
        }
        return rawValues.stream()
                .map(this::normalizeSearchText)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeSearchText(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "";
        }
        return rawValue.toLowerCase(Locale.ROOT)
                .replace("&", "and")
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private Double roundDisplayMetric(double value) {
        if (!Double.isFinite(value) || Math.abs(value) < 0.000001d) {
            return null;
        }
        return MathSupport.round(value);
    }

    private boolean isSyntheticSnapshot(StockSnapshot snapshot) {
        return snapshot != null
                && snapshot.dataVersion() != null
                && snapshot.dataVersion().toLowerCase(Locale.ROOT).contains("synthetic");
    }

    private double valueOrFloor(Double value, double fallback) {
        return value == null || !Double.isFinite(value) ? fallback : value;
    }

    private double valueOrCeiling(Double value, double fallback) {
        return value == null || !Double.isFinite(value) ? fallback : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private List<String> quickUsDiagnosticFlags(StockSnapshot snapshot, double confidence) {
        List<String> flags = new ArrayList<>();
        flags.add("quick_snapshot_only");
        flags.add("confidence_" + confidenceBucket(confidence));
        String multipleSource = parseDataVersionSignal(snapshot.dataVersion(), "mult:", "unknown");
        if (!"unknown".equals(multipleSource)) {
            flags.add("current_multiple_" + normalizeFlagToken(multipleSource));
        }
        String marketCapSource = parseDataVersionSignal(snapshot.dataVersion(), "mcap:", "unknown");
        if (!"unknown".equals(marketCapSource)) {
            flags.add("market_cap_" + normalizeFlagToken(marketCapSource));
        }
        return flags.stream().distinct().toList();
    }

    private String parseDataVersionSignal(String dataVersion, String prefix, String fallback) {
        if (dataVersion == null || dataVersion.isBlank() || prefix == null || prefix.isBlank()) {
            return fallback;
        }
        for (String token : dataVersion.split("\\|")) {
            String trimmed = token == null ? "" : token.trim();
            if (trimmed.toLowerCase(java.util.Locale.ROOT).startsWith(prefix.toLowerCase(java.util.Locale.ROOT))) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return fallback;
    }

    private String confidenceBucket(double confidenceLevel) {
        if (confidenceLevel >= 0.75) {
            return "high";
        }
        if (confidenceLevel >= 0.55) {
            return "medium";
        }
        return "low";
    }

    private String normalizeFlagToken(String value) {
        return value == null ? "unknown" : value.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    }

    private ScreenerCriteria resolveScreenerCriteria(ScreenerRequest request) {
        return new ScreenerCriteria(
                normalizeQuery(request.query()),
                normalizeFacetSelections(request.exchanges()),
                normalizeFacetSelections(request.sectors()),
                normalizeFacetSelections(request.industries()),
                normalizeFacetSelections(request.companyTypes()),
                request.minUndervalued() == null ? 0.0 : request.minUndervalued(),
                request.minRoe() == null ? 0.0 : request.minRoe(),
                request.maxPb() == null ? Double.MAX_VALUE : request.maxPb(),
                request.minDividendYield() == null ? 0.0 : request.minDividendYield(),
                request.minPrice() == null ? 0.0 : request.minPrice(),
                request.maxPrice() == null ? Double.MAX_VALUE : request.maxPrice(),
                request.minConfidence() == null ? 0.0 : request.minConfidence(),
                Boolean.TRUE.equals(request.requirePositiveFcf()),
                Boolean.TRUE.equals(request.requireStoredValuation()),
                request.minPe() == null ? 0.0 : request.minPe(),
                request.maxPe() == null ? Double.MAX_VALUE : request.maxPe(),
                request.minEvEbitda() == null ? 0.0 : request.minEvEbitda(),
                request.maxEvEbitda() == null ? Double.MAX_VALUE : request.maxEvEbitda(),
                request.minRevenueGrowth() == null ? Double.NEGATIVE_INFINITY : request.minRevenueGrowth(),
                request.minBuybackYield() == null ? 0.0 : request.minBuybackYield(),
                request.minNetCashToMarketCap() == null ? Double.NEGATIVE_INFINITY : request.minNetCashToMarketCap(),
                request.minAnalystCoverage() == null ? 0.0 : request.minAnalystCoverage(),
                request.minLiquidityScore() == null ? 0.0 : request.minLiquidityScore(),
                request.maxEarningsVolatility() == null ? Double.MAX_VALUE : request.maxEarningsVolatility(),
                request.maxDataFreshnessDays() == null ? Double.MAX_VALUE : request.maxDataFreshnessDays(),
                request.minDataCompleteness() == null ? Double.NEGATIVE_INFINITY : request.minDataCompleteness(),
                request.minMarginOfSafety() == null ? Double.NEGATIVE_INFINITY : request.minMarginOfSafety(),
                request.minQualityScore() == null ? 0.0 : request.minQualityScore(),
                normalizeFacetSelections(request.riskLevels()),
                Boolean.TRUE.equals(request.excludeValueTrap())
        );
    }

    private ScreenerFilterCategory filterCategory(
            String id,
            String label,
            String description,
            List<String> marketScope,
            List<ScreenerFilterDefinition> fields
    ) {
        return new ScreenerFilterCategory(id, label, description, marketScope, fields);
    }

    private ScreenerFilterDefinition toggleField(
            String key,
            String label,
            String helpText,
            List<String> marketScope,
            String status
    ) {
        return new ScreenerFilterDefinition(key, label, "toggle", status, null, helpText, null, null, null, null, null, marketScope, null);
    }

    private ScreenerFilterDefinition numberField(
            String key,
            String label,
            String helpText,
            String placeholder,
            Double min,
            Double max,
            Double step,
            String unit,
            List<String> marketScope,
            String status
    ) {
        return new ScreenerFilterDefinition(key, label, "number", status, placeholder, helpText, unit, min, max, step, false, marketScope, null);
    }

    private ScreenerFilterDefinition integerField(
            String key,
            String label,
            String helpText,
            String placeholder,
            Double min,
            Double max,
            Double step,
            List<String> marketScope,
            String status
    ) {
        return new ScreenerFilterDefinition(key, label, "integer", status, placeholder, helpText, null, min, max, step, true, marketScope, null);
    }

    private ScreenerFilterDefinition percentField(
            String key,
            String label,
            String helpText,
            String placeholder,
            Double min,
            Double max,
            Double step,
            List<String> marketScope,
            String status
    ) {
        return new ScreenerFilterDefinition(key, label, "percent", status, placeholder, helpText, "percent", min, max, step, false, marketScope, null);
    }

    private ScreenerFilterDefinition multiSelectField(
            String key,
            String label,
            String helpText,
            List<String> marketScope,
            String status,
            List<ScreenerFilterOption> options
    ) {
        return new ScreenerFilterDefinition(key, label, "multi_select", status, null, helpText, null, null, null, null, null, marketScope, options);
    }

    private <T> List<ScreenerFilterOption> facetOptions(
            List<T> source,
            Function<T, String> valueExtractor,
            Function<String, String> labelMapper
    ) {
        Map<String, Long> counts = source.stream()
                .map(valueExtractor)
                .map(this::trimToNull)
                .filter(value -> value != null)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .map(entry -> new ScreenerFilterOption(
                        entry.getKey(),
                        labelMapper.apply(entry.getKey()),
                        Math.toIntExact(entry.getValue())
                ))
                .toList();
    }

    private List<ScreenerFilterOption> facetOptions(List<String> values, Function<String, String> labelMapper) {
        return facetOptions(values, Function.identity(), labelMapper);
    }

    private List<String> mergeValues(List<String> first, List<String> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }

    private String humanizeFacetLabel(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return "";
        }
        return java.util.Arrays.stream(trimmed.split("[_-]+"))
                .filter(token -> !token.isBlank())
                .map(token -> Character.toUpperCase(token.charAt(0)) + token.substring(1))
                .collect(Collectors.joining(" "));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String normalizeTicker(String rawTicker) {
        return rawTicker.trim().toUpperCase(java.util.Locale.ROOT).replace(".US", "").replace(".", "-");
    }

    public record MarketSymbol(Market market, String symbol) {
    }

    private record ScreenerCriteria(
            String query,
            Set<String> exchanges,
            Set<String> sectors,
            Set<String> industries,
            Set<String> companyTypes,
            double minUndervalued,
            double minRoe,
            double maxPb,
            double minDividendYield,
            double minPrice,
            double maxPrice,
            double minConfidence,
            boolean requirePositiveFcf,
            boolean requireStoredValuation,
            double minPe,
            double maxPe,
            double minEvEbitda,
            double maxEvEbitda,
            double minRevenueGrowth,
            double minBuybackYield,
            double minNetCashToMarketCap,
            double minAnalystCoverage,
            double minLiquidityScore,
            double maxEarningsVolatility,
            double maxDataFreshnessDays,
            double minDataCompleteness,
            double minMarginOfSafety,
            double minQualityScore,
            Set<String> riskLevels,
            boolean excludeValueTrap
    ) {
    }

    private record ScreenerDecisionMetrics(
            Double marginOfSafety,
            Double qualityScore,
            String riskLevel,
            Boolean valueTrapFlag,
            Double riskScore
    ) {
    }

    private record EngineOutput(
            ValuationResult result,
            MarketComputation computation,
            Map<String, Double> confidenceBreakdown,
            StockSnapshot snapshot
    ) {
    }
}
