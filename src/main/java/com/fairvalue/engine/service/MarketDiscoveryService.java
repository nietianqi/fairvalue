package com.fairvalue.engine.service;

import com.fairvalue.engine.api.dto.MarketPeerItem;
import com.fairvalue.engine.api.dto.MarketPeersResponse;
import com.fairvalue.engine.api.dto.MarketRankingItem;
import com.fairvalue.engine.api.dto.MarketRankingResponse;
import com.fairvalue.engine.api.dto.cn.CnDiscoveryItem;
import com.fairvalue.engine.api.dto.cn.CnDiscoveryResponse;
import com.fairvalue.engine.api.dto.us.UsEquityProfileResponse;
import com.fairvalue.engine.api.dto.us.UsFinancialQualityResponse;
import com.fairvalue.engine.cn.CnStockValuationService;
import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.domain.StockFundamentals;
import com.fairvalue.engine.domain.StockSnapshot;
import com.fairvalue.engine.repository.FinancialDerivedMetricsRepository;
import com.fairvalue.engine.repository.MarketSnapshotRepository;
import com.fairvalue.engine.repository.ValuationLatestSnapshotRepository;
import com.fairvalue.engine.us.UsEquityValuationService;
import com.fairvalue.engine.us.UsConfiguredValuationModelsService;
import com.fairvalue.engine.us.UsFinancialDerivedMetricRecord;
import com.fairvalue.engine.us.UsMarketSnapshotRecord;
import com.fairvalue.engine.us.UsRankingCoverageMetrics;
import com.fairvalue.engine.us.UsRelativePeerComparable;
import com.fairvalue.engine.us.UsSecurityMaster;
import com.fairvalue.engine.us.UsSecurityMasterService;
import com.fairvalue.engine.us.UsStoredValuationSnapshotRecord;
import com.fairvalue.engine.valuation.ValuationResult;
import com.fairvalue.engine.valuation.strategy.MathSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MarketDiscoveryService {
    private static final String US_PEER_SOURCE = "security_master+market_snapshot+financial_standardized+financial_derived_metrics";
    private static final String US_SNAPSHOT_PEER_SOURCE = "market_data_service+valuation_latest_snapshot";
    private static final List<String> DEFAULT_US_PEER_FILTER_METRICS =
            List.of("market_cap", "revenue_growth", "fcf_margin", "roic", "usable_multiple");

    private final CnStockValuationService cnStockValuationService;
    private final MarketDataService marketDataService;
    private final ValuationService valuationService;
    private final UsEquityValuationService usEquityValuationService;
    private final UsConfiguredValuationModelsService usConfiguredValuationModelsService;
    private final UsSecurityMasterService usSecurityMasterService;
    private final ValuationLatestSnapshotRepository valuationLatestSnapshotRepository;
    private final MarketSnapshotRepository marketSnapshotRepository;
    private final FinancialDerivedMetricsRepository financialDerivedMetricsRepository;
    @Value("${app.valuation.us.rankings.strict-freshness-hours:6}")
    private int strictFreshnessHours;
    @Value("${app.valuation.us.rankings.min-confidence:0.45}")
    private double strictMinConfidence;
    @Value("${app.valuation.us.rankings.min-market-cap:50000000}")
    private double strictMinMarketCap;
    @Value("${app.valuation.us.rankings.max-market-cap:5000000000000}")
    private double strictMaxMarketCap;
    @Value("${app.valuation.us.rankings.min-coverage-ratio:0.60}")
    private double strictMinCoverageRatio;

    public MarketDiscoveryService(
            CnStockValuationService cnStockValuationService,
            MarketDataService marketDataService,
            ValuationService valuationService,
            UsEquityValuationService usEquityValuationService,
            UsConfiguredValuationModelsService usConfiguredValuationModelsService,
            UsSecurityMasterService usSecurityMasterService,
            ValuationLatestSnapshotRepository valuationLatestSnapshotRepository,
            MarketSnapshotRepository marketSnapshotRepository,
            FinancialDerivedMetricsRepository financialDerivedMetricsRepository
    ) {
        this.cnStockValuationService = cnStockValuationService;
        this.marketDataService = marketDataService;
        this.valuationService = valuationService;
        this.usEquityValuationService = usEquityValuationService;
        this.usConfiguredValuationModelsService = usConfiguredValuationModelsService;
        this.usSecurityMasterService = usSecurityMasterService;
        this.valuationLatestSnapshotRepository = valuationLatestSnapshotRepository;
        this.marketSnapshotRepository = marketSnapshotRepository;
        this.financialDerivedMetricsRepository = financialDerivedMetricsRepository;
    }

    public CnDiscoveryResponse discovery(Market market, Integer page, Integer size) {
        int resolvedPage = page == null || page < 1 ? 1 : page;
        int resolvedSize = size == null || size < 1 ? 30 : Math.min(size, 100);

        if (market == Market.CN) {
            return cnStockValuationService.discovery(resolvedPage, resolvedSize);
        }
        if (market == Market.US) {
            return usDiscovery(resolvedPage, resolvedSize);
        }

        List<StockSnapshot> snapshots = marketDataService.listSnapshots(List.of(market));
        int fromIndex = Math.min((resolvedPage - 1) * resolvedSize, snapshots.size());
        int toIndex = Math.min(fromIndex + resolvedSize, snapshots.size());

        List<CnDiscoveryItem> items = snapshots.subList(fromIndex, toIndex).stream()
                .map(snapshot -> {
                    try {
                        return market == Market.US ? toUsListItem(snapshot) : toGenericItem(snapshot);
                    } catch (Exception ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        return new CnDiscoveryResponse(
                resolvedPage,
                resolvedSize,
                snapshots.size(),
                market.name().toLowerCase(Locale.ROOT) + "-interface",
                items
        );
    }

    public MarketRankingResponse rankings(Market market, String rankingType, Integer page, Integer size) {
        int resolvedPage = page == null || page < 1 ? 1 : page;
        int resolvedSize = size == null || size < 1 ? 30 : Math.min(size, 100);
        String resolvedRankingType = normalizeRankingType(rankingType);
        Instant generatedAt = Instant.now();
        LocalDate dataAsOf = LocalDate.now();

        if (market == Market.CN) {
            CnDiscoveryResponse discovery = cnStockValuationService.discovery(resolvedPage, resolvedSize);
            List<MarketRankingItem> ranked = discovery.items().stream()
                    .map(this::toRankingItem)
                    .sorted(rankingComparator(resolvedRankingType))
                    .toList();
            return new MarketRankingResponse(
                    market.name(),
                    resolvedRankingType,
                    resolvedPage,
                    resolvedSize,
                    discovery.total(),
                    discovery.source() + "|page-scoped-ranking",
                    generatedAt,
                    dataAsOf,
                    "upside_pct",
                    "Current CN ranking is based on the loaded discovery page subset, not the full market universe.",
                    ranked
            );
        }

        if (market == Market.US) {
            UsRankingCoverageMetrics coverage = usRankingCoverage();
            if (coverage.strictReady()) {
                List<MarketRankingItem> snapshotItems = valuationLatestSnapshotRepository.findStrictRankings(
                        Market.US.name(),
                        resolvedRankingType,
                        strictRankingFreshnessCutoff(),
                        strictMinConfidence,
                        strictMinMarketCap,
                        strictMaxMarketCap,
                        resolvedSize,
                        Math.max((resolvedPage - 1) * resolvedSize, 0)
                );
                return new MarketRankingResponse(
                        market.name(),
                        resolvedRankingType,
                        resolvedPage,
                        resolvedSize,
                        coverage.rankableCount(),
                        "us_latest_snapshot_strict_ranking",
                        generatedAt,
                        valuationLatestSnapshotRepository.latestDataAsOf(Market.US.name()),
                        "upside_pct",
                        coverage.disclaimer(),
                        snapshotItems
                );
            }

            List<MarketRankingItem> pageScopedItems = usSecurityMasterService.findActiveUsUniversePage(resolvedPage, resolvedSize).stream()
                    .map(this::toUsRankingItem)
                    .filter(Objects::nonNull)
                    .sorted(rankingComparator(resolvedRankingType))
                    .toList();
            return new MarketRankingResponse(
                market.name(),
                resolvedRankingType,
                resolvedPage,
                resolvedSize,
                coverage.universeSize(),
                "us_security_master_page_scoped_ranking|full-universe-paged",
                generatedAt,
                coverage.snapshotCount() > 0 ? valuationLatestSnapshotRepository.latestDataAsOf(Market.US.name()) : dataAsOf,
                "upside_pct",
                coverage.disclaimer(),
                pageScopedItems
            );
        }

        List<MarketRankingItem> rankedItems = marketDataService.listSnapshots(List.of(market)).stream()
                .map(snapshot -> {
                    try {
                        return market == Market.US ? toUsRankingItem(snapshot) : toRankingItem(toGenericItem(snapshot));
                    } catch (Exception ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .sorted(rankingComparator(resolvedRankingType))
                .toList();

        int fromIndex = Math.min((resolvedPage - 1) * resolvedSize, rankedItems.size());
        int toIndex = Math.min(fromIndex + resolvedSize, rankedItems.size());

        return new MarketRankingResponse(
                market.name(),
                resolvedRankingType,
                resolvedPage,
                resolvedSize,
                rankedItems.size(),
                market.name().toLowerCase(Locale.ROOT) + "_snapshot_ranking",
                generatedAt,
                dataAsOf,
                "upside_pct",
                "",
                rankedItems.subList(fromIndex, toIndex)
        );
    }

    public MarketPeersResponse peers(Market market, String symbol, Integer limit) {
        int resolvedLimit = limit == null || limit < 1 ? 5 : Math.min(limit, 12);
        return market == Market.US
                ? usPeers(symbol, resolvedLimit)
                : genericPeers(market, symbol, resolvedLimit);
    }

    public UsRankingCoverageMetrics usRankingCoverage() {
        long universeSize = usSecurityMasterService.countActiveUsUniverse();
        long snapshotCount = valuationLatestSnapshotRepository.countByMarket(Market.US.name());
        Instant staleBefore = strictRankingFreshnessCutoff();
        long staleCount = snapshotCount == 0
                ? 0
                : valuationLatestSnapshotRepository.countStaleByMarket(Market.US.name(), staleBefore);
        long rankableCount = snapshotCount == 0
                ? 0
                : valuationLatestSnapshotRepository.countRankableByMarket(
                Market.US.name(),
                staleBefore,
                strictMinConfidence,
                strictMinMarketCap,
                strictMaxMarketCap
        );
        double snapshotCoverage = universeSize <= 0 ? 0.0 : (double) snapshotCount / (double) universeSize;
        double rankableCoverage = universeSize <= 0 ? 0.0 : (double) rankableCount / (double) universeSize;
        double staleRatio = snapshotCount <= 0 ? 1.0 : (double) staleCount / (double) snapshotCount;
        boolean strictReady = universeSize > 0
                && rankableCount > 0
                && rankableCoverage >= strictMinCoverageRatio;
        String disclaimer = strictReady
                ? ""
                : "Current US ranking is paged across the full security master universe; until latest snapshots meet strict coverage and freshness thresholds, ordering remains page-scoped within each loaded page.";
        return new UsRankingCoverageMetrics(
                Instant.now(),
                universeSize,
                snapshotCount,
                rankableCount,
                staleCount,
                MathSupport.round(snapshotCoverage),
                MathSupport.round(rankableCoverage),
                MathSupport.round(staleRatio),
                strictReady,
                strictReady ? "strict_persisted_ranking" : "page_scoped_ranking",
                disclaimer
        );
    }

    private CnDiscoveryResponse usDiscovery(int page, int size) {
        long total = usSecurityMasterService.countActiveUsUniverse();
        List<CnDiscoveryItem> items = usSecurityMasterService.findActiveUsUniversePage(page, size).stream()
                .map(this::toUsListItem)
                .filter(Objects::nonNull)
                .toList();
        return new CnDiscoveryResponse(
                page,
                size,
                total,
                "us_security_master_paged",
                items
        );
    }

    private CnDiscoveryItem toUsItem(StockSnapshot snapshot) {
        String ticker = snapshot.symbol();
        StockSnapshot liveSnapshot = marketDataService.getSnapshot(Market.US, ticker);
        ValuationResult valuation = valuationService.valuate(Market.US, ticker);
        UsEquityProfileResponse profile = usEquityValuationService.profile(ticker);
        UsFinancialQualityResponse quality = usEquityValuationService.financialQuality(ticker);

        int confidenceScore = normalizeConfidence(valuation.confidence());
        int totalQualityScore = normalizeConfidence(quality.totalQualityScore());
        int earningsQualityScore = normalizeConfidence(quality.earningsQualityScore());
        int revenueQualityScore = normalizeConfidence(quality.revenueQualityScore());
        int capitalEfficiencyScore = normalizeConfidence(quality.capitalEfficiencyScore());

        return new CnDiscoveryItem(
                ticker,
                profile.companyName(),
                profile.industry(),
                valuation.price(),
                valuation.tradableFairValue(),
                valuation.fairValueLow(),
                valuation.fairValueHigh(),
                valuation.upside(),
                confidenceScore,
                verdictLabel(valuation.upside()),
                gradeLabel(totalQualityScore),
                gradeLabel(earningsQualityScore),
                gradeLabel(revenueQualityScore),
                gradeLabel(capitalEfficiencyScore),
                analystRating(valuation.upside(), confidenceScore),
                MathSupport.round(profile.pe()),
                MathSupport.round(profile.evEbitda()),
                MathSupport.round(profile.marketCap() / 100000000.0),
                MathSupport.round(profile.earningsGrowth5y()),
                MathSupport.round(profile.dailyChange()),
                MathSupport.round(liveSnapshot.fundamentals().liquidityScore() * 100.0)
        );
    }

    private CnDiscoveryItem toUsListItem(StockSnapshot snapshot) {
        QuickUsListValuation valuation = quickUsListValuation(snapshot);
        StockFundamentals fundamentals = snapshot.fundamentals();

        int confidenceScore = normalizeConfidence(valuation.confidence());
        int qualityScore = scoreFromRatio(Math.max(fundamentals.roe(), fundamentals.fcfMargin()), 60);
        int growthScore = scoreFromRatio(fundamentals.revenueGrowth(), 62);
        int cashflowScore = fundamentals.positiveFreeCashFlow()
                ? Math.max(confidenceScore - 5, 45)
                : 28;

        return new CnDiscoveryItem(
                snapshot.symbol(),
                snapshot.companyName(),
                snapshot.industry(),
                valuation.price(),
                valuation.fairValueMid(),
                valuation.fairValueLow(),
                valuation.fairValueHigh(),
                valuation.upside(),
                confidenceScore,
                verdictLabel(valuation.upside()),
                gradeLabel(qualityScore),
                gradeLabel(cashflowScore),
                gradeLabel(growthScore),
                gradeLabel(qualityScore),
                analystRating(valuation.upside(), confidenceScore),
                MathSupport.round(fundamentals.pe()),
                MathSupport.round(fundamentals.evEbitda()),
                0.0,
                MathSupport.round(fundamentals.revenueGrowth()),
                0.0,
                MathSupport.round(fundamentals.liquidityScore() * 100.0)
        );
    }

    private CnDiscoveryItem toUsListItem(UsSecurityMaster security) {
        UsStoredValuationSnapshotRecord storedSnapshot = valuationLatestSnapshotRepository.findByTicker(security.ticker()).orElse(null);
        if (storedSnapshot != null) {
            UsMarketSnapshotRecord latestMarketSnapshot = latestMarketSnapshot(security.ticker(), storedSnapshot);
            UsFinancialDerivedMetricRecord latestDerived = latestDerivedMetrics(security.ticker(), storedSnapshot);
            return toUsStoredListItem(security, storedSnapshot, latestMarketSnapshot, latestDerived);
        }
        return toUsListItem(enrichUsSnapshot(security, marketDataService.getSnapshot(Market.US, security.ticker())));
    }

    private MarketRankingItem toUsRankingItem(StockSnapshot snapshot) {
        QuickUsListValuation valuation = quickUsListValuation(snapshot);
        StockFundamentals fundamentals = snapshot.fundamentals();

        return new MarketRankingItem(
                snapshot.symbol(),
                snapshot.companyName(),
                snapshot.industry(),
                MathSupport.round(valuation.price()),
                MathSupport.round(valuation.fairValueMid()),
                MathSupport.round(valuation.fairValueLow()),
                MathSupport.round(valuation.fairValueHigh()),
                MathSupport.round(valuation.upside()),
                MathSupport.round(valuation.confidence()),
                rankingVerdict(valuation.upside()),
                MathSupport.round(fundamentals.pe()),
                MathSupport.round(fundamentals.evEbitda()),
                null,
                MathSupport.round(fundamentals.revenueGrowth()),
                null,
                MathSupport.round(fundamentals.liquidityScore() * 100.0)
        );
    }

    private MarketRankingItem toUsRankingItem(UsSecurityMaster security) {
        UsStoredValuationSnapshotRecord storedSnapshot = valuationLatestSnapshotRepository.findByTicker(security.ticker()).orElse(null);
        if (storedSnapshot != null) {
            UsMarketSnapshotRecord latestMarketSnapshot = latestMarketSnapshot(security.ticker(), storedSnapshot);
            UsFinancialDerivedMetricRecord latestDerived = latestDerivedMetrics(security.ticker(), storedSnapshot);
            return toUsStoredRankingItem(security, storedSnapshot, latestMarketSnapshot, latestDerived);
        }
        return toUsRankingItem(enrichUsSnapshot(security, marketDataService.getSnapshot(Market.US, security.ticker())));
    }

    private CnDiscoveryItem toUsStoredListItem(
            UsSecurityMaster security,
            UsStoredValuationSnapshotRecord storedSnapshot,
            UsMarketSnapshotRecord latestMarketSnapshot,
            UsFinancialDerivedMetricRecord latestDerived
    ) {
        double upside = storedSnapshot.upsidePct();
        int confidenceScore = normalizeConfidence(storedSnapshot.confidenceLevel());
        double growthMetric = latestDerived != null && latestDerived.epsDiluted() != null
                ? latestDerived.epsDiluted().doubleValue()
                : 0.0;
        double dailyChange = 0.0;
        return new CnDiscoveryItem(
                security.ticker(),
                firstNonBlank(security.companyName(), security.ticker()),
                firstNonBlank(security.industry(), firstNonBlank(security.sector(), security.exchange())),
                MathSupport.round(storedSnapshot.currentPrice()),
                MathSupport.round(storedSnapshot.fairValueMid()),
                MathSupport.round(storedSnapshot.fairValueLow()),
                MathSupport.round(storedSnapshot.fairValueHigh()),
                MathSupport.round(upside),
                confidenceScore,
                verdictLabel(upside),
                gradeLabel(scoreFromStored(storedSnapshot.qualityScore(), confidenceScore)),
                gradeLabel(scoreFromStored(latestDerived == null ? null : latestDerived.fcfMargin(), confidenceScore)),
                gradeLabel(scoreFromStored(latestDerived == null ? null : latestDerived.roic(), confidenceScore)),
                gradeLabel(scoreFromStored(latestDerived == null ? null : latestDerived.roe(), confidenceScore)),
                analystRating(upside, confidenceScore),
                latestMarketSnapshot == null || latestMarketSnapshot.peTtmVendor() == null ? 0.0 : MathSupport.round(latestMarketSnapshot.peTtmVendor().doubleValue()),
                0.0,
                latestMarketSnapshot == null || latestMarketSnapshot.marketCapVendor() == null ? 0.0 : MathSupport.round(latestMarketSnapshot.marketCapVendor().doubleValue() / 100000000.0),
                MathSupport.round(growthMetric),
                MathSupport.round(dailyChange),
                MathSupport.round(storedSnapshot.confidenceLevel() * 100.0)
        );
    }

    private MarketRankingItem toUsStoredRankingItem(
            UsSecurityMaster security,
            UsStoredValuationSnapshotRecord storedSnapshot,
            UsMarketSnapshotRecord latestMarketSnapshot,
            UsFinancialDerivedMetricRecord latestDerived
    ) {
        return new MarketRankingItem(
                security.ticker(),
                firstNonBlank(security.companyName(), security.ticker()),
                firstNonBlank(security.industry(), firstNonBlank(security.sector(), security.exchange())),
                MathSupport.round(storedSnapshot.currentPrice()),
                MathSupport.round(storedSnapshot.fairValueMid()),
                MathSupport.round(storedSnapshot.fairValueLow()),
                MathSupport.round(storedSnapshot.fairValueHigh()),
                MathSupport.round(storedSnapshot.upsidePct()),
                MathSupport.round(storedSnapshot.confidenceLevel()),
                normalizeVerdict(storedSnapshot.finalVerdict(), storedSnapshot.upsidePct()),
                latestMarketSnapshot == null || latestMarketSnapshot.peTtmVendor() == null ? null : MathSupport.round(latestMarketSnapshot.peTtmVendor().doubleValue()),
                null,
                latestMarketSnapshot == null || latestMarketSnapshot.marketCapVendor() == null ? null : MathSupport.round(latestMarketSnapshot.marketCapVendor().doubleValue()),
                latestDerived == null || latestDerived.epsDiluted() == null ? null : MathSupport.round(latestDerived.epsDiluted().doubleValue()),
                null,
                MathSupport.round(storedSnapshot.confidenceLevel() * 100.0)
        );
    }

    private StockSnapshot enrichUsSnapshot(UsSecurityMaster security, StockSnapshot snapshot) {
        return new StockSnapshot(
                snapshot.market(),
                security.ticker(),
                snapshot.currency(),
                firstNonBlank(security.companyName(), snapshot.companyName()),
                firstNonBlank(security.industry(), firstNonBlank(security.sector(), snapshot.industry())),
                snapshot.price(),
                snapshot.fundamentals(),
                snapshot.dataVersion()
        );
    }

    private QuickUsListValuation quickUsListValuation(StockSnapshot snapshot) {
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
        double upside = fairValueMid / snapshot.price() - 1.0;
        return new QuickUsListValuation(
                MathSupport.round(snapshot.price()),
                MathSupport.round(fairValueMid),
                MathSupport.round(fairValueLow),
                MathSupport.round(fairValueHigh),
                MathSupport.round(upside),
                MathSupport.round(confidence)
        );
    }

    private CnDiscoveryItem toGenericItem(StockSnapshot snapshot) {
        Market market = snapshot.market();
        StockFundamentals fundamentals = snapshot.fundamentals();
        ValuationResult valuation = valuationService.valuate(market, snapshot.symbol());
        int confidenceScore = normalizeConfidence(valuation.confidence());

        return new CnDiscoveryItem(
                snapshot.symbol(),
                snapshot.companyName(),
                snapshot.industry(),
                valuation.price(),
                valuation.tradableFairValue(),
                valuation.fairValueLow(),
                valuation.fairValueHigh(),
                valuation.upside(),
                confidenceScore,
                verdictLabel(valuation.upside()),
                gradeLabel(confidenceScore),
                fundamentals.positiveFreeCashFlow() ? gradeLabel(Math.max(confidenceScore - 5, 45)) : "差",
                gradeLabel(scoreFromRatio(fundamentals.revenueGrowth(), 62)),
                gradeLabel(scoreFromRatio(Math.max(fundamentals.roe(), fundamentals.fcfMargin()), 60)),
                analystRating(valuation.upside(), confidenceScore),
                MathSupport.round(fundamentals.pe()),
                MathSupport.round(fundamentals.evEbitda()),
                0.0,
                MathSupport.round(fundamentals.revenueGrowth()),
                0.0,
                MathSupport.round(fundamentals.liquidityScore() * 100.0)
        );
    }

    private MarketRankingItem toRankingItem(CnDiscoveryItem item) {
        return new MarketRankingItem(
                item.ticker(),
                item.name(),
                item.industry(),
                item.price(),
                item.fairValue(),
                item.fairValueLow(),
                item.fairValueHigh(),
                item.upside(),
                (double) item.confidenceScore(),
                rankingVerdict(item.upside()),
                item.pe(),
                item.evEbitda(),
                item.marketCap(),
                item.eps5y(),
                item.dailyChange(),
                item.turnover()
        );
    }

    private MarketPeersResponse usPeers(String ticker, int limit) {
        String normalizedTicker = ticker.trim().toUpperCase(Locale.ROOT);
        Optional<UsSecurityMaster> targetSecurity = usSecurityMasterService.findByTicker(normalizedTicker);
        if (targetSecurity.isEmpty() || targetSecurity.get().id() == null) {
            return fallbackUsSnapshotPeers(normalizedTicker, null, limit, "no_security_context");
        }

        UsSecurityMaster security = targetSecurity.get();
        StockSnapshot targetSnapshot = marketDataService.getSnapshot(Market.US, normalizedTicker);
        Map<String, UsRelativePeerComparable> candidateMap = loadUsPeerCandidates(security);
        Map<String, UsStoredValuationSnapshotRecord> storedSnapshotMap = valuationLatestSnapshotRepository.findAllByMarket(Market.US.name()).stream()
                .collect(Collectors.toMap(
                        record -> record.ticker().toUpperCase(Locale.ROOT),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        UsConfiguredValuationModelsService.RelativePeerSelectionView selection =
                usConfiguredValuationModelsService.selectRelativePeersReadOnly(
                        usConfiguredValuationModelsService.loadContext(targetSnapshot, security),
                        limit
                );

        List<String> peerTickers = selection.peers().stream()
                .map(UsRelativePeerComparable::ticker)
                .toList();
        Map<String, Long> selectionBreakdown = selection.selectionBreakdown();
        List<String> filterMetrics = selection.filterMetrics();
        String selectionBasis = selection.selectionBasis();
        String sourceMode = selection.sourceMode();
        String peerSetSource = selection.peerSetSource();
        Integer peerCandidateCount = selection.candidateCount();
        String ruleVersion = selection.ruleVersion();
        String filterSummary = selection.filterSummary();

        List<MarketPeerItem> items = peerTickers.stream()
                .limit(limit)
                .map(peerTicker -> toUsPeerItem(
                        peerTicker,
                        security,
                        candidateMap.get(peerTicker.toUpperCase(Locale.ROOT)),
                        storedSnapshotMap.get(peerTicker.toUpperCase(Locale.ROOT))
                ))
                .toList();

        if (items.isEmpty()) {
            return fallbackUsSnapshotPeers(normalizedTicker, security, limit, "no_clean_peer_set");
        }

        return new MarketPeersResponse(
                Market.US.name(),
                normalizedTicker,
                firstNonBlank(selectionBasis, items.isEmpty() ? "template_only" : "mixed"),
                selection.selectionMode(),
                firstNonBlank(sourceMode, items.isEmpty() ? "template_only" : "peer_set_plus_template"),
                firstNonBlank(peerSetSource, items.isEmpty() ? "template_only" : US_PEER_SOURCE),
                peerCandidateCount == null ? candidateMap.size() : peerCandidateCount,
                firstNonBlank(ruleVersion, items.isEmpty() ? null : "v3_industry_specific"),
                firstNonBlank(filterSummary, items.isEmpty() ? "no_clean_peer_set" : "strict_us_peer_selection"),
                selectionBreakdown,
                filterMetrics.isEmpty() ? DEFAULT_US_PEER_FILTER_METRICS : filterMetrics,
                items
        );
    }

    private MarketPeersResponse fallbackUsSnapshotPeers(String ticker, UsSecurityMaster targetSecurity, int limit, String reason) {
        StockSnapshot target = marketDataService.getSnapshot(Market.US, ticker);
        Map<String, UsStoredValuationSnapshotRecord> storedSnapshotMap = valuationLatestSnapshotRepository.findAllByMarket(Market.US.name()).stream()
                .collect(Collectors.toMap(
                        record -> record.ticker().toUpperCase(Locale.ROOT),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<StockSnapshot> selected = marketDataService.listSnapshots(List.of(Market.US)).stream()
                .filter(snapshot -> !snapshot.symbol().equalsIgnoreCase(target.symbol()))
                .sorted(Comparator
                        .comparingInt((StockSnapshot snapshot) -> snapshotPeerGroupPriority(
                                target,
                                targetSecurity,
                                snapshot,
                                storedSnapshotMap.get(snapshot.symbol().toUpperCase(Locale.ROOT))
                        ))
                        .thenComparingDouble(snapshot -> usSnapshotPeerDistance(target, snapshot))
                        .thenComparing(Comparator.comparingDouble((StockSnapshot snapshot) -> snapshot.fundamentals().liquidityScore()).reversed()))
                .limit(limit)
                .toList();

        Map<String, Long> selectionBreakdown = new LinkedHashMap<>();
        selected.forEach(snapshot -> selectionBreakdown.merge(
                snapshotPeerGroupLabel(target, targetSecurity, snapshot, storedSnapshotMap.get(snapshot.symbol().toUpperCase(Locale.ROOT))),
                1L,
                Long::sum
        ));

        String selectionBasis = selectionBreakdown.containsKey("industry")
                ? "snapshot_industry"
                : selectionBreakdown.containsKey("sector_template")
                ? "snapshot_sector_template"
                : selectionBreakdown.containsKey("company_type")
                ? "snapshot_company_type"
                : selected.isEmpty() ? "template_only" : "snapshot_fundamentals";

        List<MarketPeerItem> items = selected.stream()
                .map(snapshot -> toUsSnapshotPeerItem(
                        target,
                        snapshot,
                        storedSnapshotMap.get(snapshot.symbol().toUpperCase(Locale.ROOT)),
                        latestMarketSnapshot(snapshot.symbol(), storedSnapshotMap.get(snapshot.symbol().toUpperCase(Locale.ROOT))),
                        latestDerivedMetrics(snapshot.symbol(), storedSnapshotMap.get(snapshot.symbol().toUpperCase(Locale.ROOT)))
                ))
                .toList();

        String filterSummary = selected.isEmpty()
                ? reason
                : "basis=" + selectionBasis + ", selected=" + selected.size() + ", mode=read_only_snapshot_fallback";

        return new MarketPeersResponse(
                Market.US.name(),
                target.symbol().toUpperCase(Locale.ROOT),
                selectionBasis,
                selected.isEmpty() ? "template_only" : "snapshot_fallback",
                selected.isEmpty() ? "template_only" : "snapshot_fallback",
                selected.isEmpty() ? "template_only" : US_SNAPSHOT_PEER_SOURCE,
                Math.max(selected.size(), 0),
                selected.isEmpty() ? null : "v2_snapshot_fallback",
                filterSummary,
                selectionBreakdown,
                List.of("industry", "sector_template", "company_type", "market_cap", "pe", "revenue_growth", "fcf_margin", "roic", "liquidity"),
                items
        );
    }

    private Map<String, UsRelativePeerComparable> loadUsPeerCandidates(UsSecurityMaster targetSecurity) {
        return usSecurityMasterService.findRelativePeers(targetSecurity.id(), targetSecurity, 48).stream()
                .collect(Collectors.toMap(
                        peer -> peer.ticker().toUpperCase(Locale.ROOT),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private MarketPeerItem toUsPeerItem(
            String ticker,
            UsSecurityMaster targetSecurity,
            UsRelativePeerComparable comparable,
            UsStoredValuationSnapshotRecord storedSnapshot
    ) {
        String normalizedTicker = ticker.toUpperCase(Locale.ROOT);
        Double revenueGrowth = comparable != null && comparable.revenueGrowthProxy() != null
                ? comparable.revenueGrowthProxy().doubleValue()
                : null;
        Double fcfMargin = comparable != null && comparable.fcfMargin() != null
                ? comparable.fcfMargin().doubleValue()
                : null;
        Double roic = comparable != null && comparable.roic() != null
                ? comparable.roic().doubleValue()
                : null;
        Double evEbitda = comparable != null ? impliedEvEbitda(comparable) : null;

        if (storedSnapshot != null) {
            return new MarketPeerItem(
                    normalizedTicker,
                    firstNonBlank(comparable == null ? null : comparable.companyName(), normalizedTicker),
                    firstNonBlank(comparable == null ? null : comparable.industry(), comparable == null ? null : comparable.sector()),
                    usPeerMatchBasis(targetSecurity, comparable),
                    MathSupport.round(storedSnapshot.currentPrice()),
                    MathSupport.round(storedSnapshot.fairValueMid()),
                    MathSupport.round(storedSnapshot.upsidePct()),
                    comparable != null && comparable.peTtm() != null ? MathSupport.round(comparable.peTtm().doubleValue()) : null,
                    evEbitda == null ? null : MathSupport.round(evEbitda),
                    comparable != null && comparable.marketCap() != null ? MathSupport.round(comparable.marketCap().doubleValue()) : null,
                    revenueGrowth == null ? null : MathSupport.round(revenueGrowth),
                    fcfMargin == null ? null : MathSupport.round(fcfMargin),
                    roic == null ? null : MathSupport.round(roic),
                    MathSupport.round(storedSnapshot.confidenceLevel()),
                    storedSnapshot.qualityScore() == null ? null : MathSupport.round(storedSnapshot.qualityScore()),
                    storedSnapshot.qualityScore() == null ? null : MathSupport.round(storedSnapshot.qualityScore()),
                    MathSupport.round(peerDataCompleteness(
                            comparable != null && comparable.marketCap() != null,
                            revenueGrowth != null,
                            fcfMargin != null,
                            roic != null,
                            comparable != null && comparable.peTtm() != null,
                            evEbitda != null
                    )),
                    normalizeVerdict(storedSnapshot.finalVerdict(), storedSnapshot.upsidePct())
            );
        }

        ValuationResult valuation = valuationService.valuate(Market.US, ticker);
        UsEquityProfileResponse profile = usEquityValuationService.profile(ticker);
        UsFinancialQualityResponse quality = usEquityValuationService.financialQuality(ticker);
        StockSnapshot snapshot = marketDataService.getSnapshot(Market.US, ticker);
        if (revenueGrowth == null) {
            revenueGrowth = snapshot.fundamentals().revenueGrowth();
        }
        if (fcfMargin == null) {
            fcfMargin = snapshot.fundamentals().fcfMargin();
        }

        return new MarketPeerItem(
                normalizedTicker,
                profile.companyName(),
                profile.industry(),
                usPeerMatchBasis(targetSecurity, comparable),
                MathSupport.round(valuation.price()),
                MathSupport.round(valuation.tradableFairValue()),
                MathSupport.round(valuation.upside()),
                MathSupport.round(profile.pe()),
                MathSupport.round(profile.evEbitda()),
                MathSupport.round(profile.marketCap()),
                revenueGrowth == null ? null : MathSupport.round(revenueGrowth),
                fcfMargin == null ? null : MathSupport.round(fcfMargin),
                roic == null ? null : MathSupport.round(roic),
                MathSupport.round(valuation.confidence()),
                MathSupport.round(quality.totalQualityScore()),
                MathSupport.round(quality.totalQualityScore()),
                MathSupport.round(peerDataCompleteness(
                        profile.marketCap() > 0.0,
                        revenueGrowth != null,
                        fcfMargin != null,
                        roic != null,
                        profile.pe() > 0.0,
                        profile.evEbitda() > 0.0
                )),
                normalizeVerdict(valuation.valuationStatus() == null ? null : valuation.valuationStatus().name(), valuation.upside())
        );
    }

    private Double impliedEvEbitda(UsRelativePeerComparable comparable) {
        if (comparable == null || comparable.marketCap() == null || comparable.ebitda() == null || comparable.ebitda().doubleValue() <= 0.0) {
            return null;
        }
        double marketCap = comparable.marketCap().doubleValue();
        double netDebt = comparable.netDebt() == null ? 0.0 : comparable.netDebt().doubleValue();
        return (marketCap + netDebt) / comparable.ebitda().doubleValue();
    }

    private MarketPeersResponse genericPeers(Market market, String symbol, int limit) {
        StockSnapshot target = marketDataService.getSnapshot(market, symbol);
        List<StockSnapshot> candidates = marketDataService.listSnapshots(List.of(market)).stream()
                .filter(snapshot -> !snapshot.symbol().equalsIgnoreCase(target.symbol()))
                .sorted(Comparator
                        .comparingInt((StockSnapshot snapshot) -> sameIndustry(target, snapshot) ? 0 : 1)
                        .thenComparingDouble(snapshot -> genericMarketCapDistance(target, snapshot))
                        .thenComparing(Comparator.comparingDouble((StockSnapshot snapshot) -> snapshot.fundamentals().liquidityScore()).reversed()))
                .toList();

        List<StockSnapshot> selected = candidates.stream()
                .limit(limit)
                .toList();

        Map<String, Long> selectionBreakdown = new LinkedHashMap<>();
        selected.forEach(snapshot -> selectionBreakdown.merge(
                sameIndustry(target, snapshot) ? "industry" : "market",
                1L,
                Long::sum
        ));

        String selectionBasis = selectionBreakdown.containsKey("industry") ? "industry" : "market";
        List<MarketPeerItem> items = selected.stream()
                .map(snapshot -> toGenericPeerItem(target, snapshot))
                .toList();

        return new MarketPeersResponse(
                market.name(),
                target.symbol(),
                selectionBasis,
                "snapshot_universe",
                "snapshot_universe",
                market.name().toLowerCase(Locale.ROOT) + "_snapshot_universe",
                candidates.size(),
                "v1_basic",
                "industry-preferred snapshot peer selection",
                selectionBreakdown,
                List.of("industry", "market_cap", "liquidity"),
                items
        );
    }

    private MarketPeerItem toGenericPeerItem(StockSnapshot target, StockSnapshot candidate) {
        ValuationResult valuation = valuationService.valuate(candidate.market(), candidate.symbol());
        Double qualityScore = scoreFromRatio(
                Math.max(candidate.fundamentals().roe(), candidate.fundamentals().fcfMargin()),
                60
        ) / 100.0;

        return new MarketPeerItem(
                candidate.symbol(),
                candidate.companyName(),
                candidate.industry(),
                sameIndustry(target, candidate) ? "industry" : "market",
                MathSupport.round(valuation.price()),
                MathSupport.round(valuation.tradableFairValue()),
                MathSupport.round(valuation.upside()),
                MathSupport.round(candidate.fundamentals().pe()),
                MathSupport.round(candidate.fundamentals().evEbitda()),
                genericMarketCap(candidate),
                MathSupport.round(candidate.fundamentals().revenueGrowth()),
                MathSupport.round(candidate.fundamentals().fcfMargin()),
                null,
                MathSupport.round(valuation.confidence()),
                MathSupport.round(qualityScore),
                MathSupport.round(qualityScore),
                MathSupport.round(peerDataCompleteness(
                        genericMarketCap(candidate) != null,
                        true,
                        true,
                        false,
                        candidate.fundamentals().pe() > 0.0,
                        candidate.fundamentals().evEbitda() > 0.0
                )),
                valuation.valuationStatus() == null ? rankingVerdict(valuation.upside()) : valuation.valuationStatus().name()
        );
    }

    private double genericMarketCapDistance(StockSnapshot target, StockSnapshot candidate) {
        Double targetMarketCap = genericMarketCap(target);
        Double candidateMarketCap = genericMarketCap(candidate);
        if (targetMarketCap == null || targetMarketCap <= 0.0 || candidateMarketCap == null || candidateMarketCap <= 0.0) {
            return Double.MAX_VALUE / 4.0;
        }
        return Math.abs(Math.log(candidateMarketCap / targetMarketCap));
    }

    private Double genericMarketCap(StockSnapshot snapshot) {
        return null;
    }

    private int snapshotPeerGroupPriority(
            StockSnapshot target,
            UsSecurityMaster targetSecurity,
            StockSnapshot candidate,
            UsStoredValuationSnapshotRecord storedSnapshot
    ) {
        if (sameIndustry(target, candidate)) {
            return 0;
        }
        if (targetSecurity != null && storedSnapshot != null && sameValue(targetSecurity.sectorTemplate(), storedSnapshot.sectorTemplate())) {
            return 1;
        }
        if (targetSecurity != null && storedSnapshot != null && sameValue(targetSecurity.companyType(), storedSnapshot.companyType())) {
            return 2;
        }
        return 3;
    }

    private String snapshotPeerGroupLabel(
            StockSnapshot target,
            UsSecurityMaster targetSecurity,
            StockSnapshot candidate,
            UsStoredValuationSnapshotRecord storedSnapshot
    ) {
        if (sameIndustry(target, candidate)) {
            return "industry";
        }
        if (targetSecurity != null && storedSnapshot != null && sameValue(targetSecurity.sectorTemplate(), storedSnapshot.sectorTemplate())) {
            return "sector_template";
        }
        if (targetSecurity != null && storedSnapshot != null && sameValue(targetSecurity.companyType(), storedSnapshot.companyType())) {
            return "company_type";
        }
        return "snapshot_fundamentals";
    }

    private double usSnapshotPeerDistance(StockSnapshot target, StockSnapshot candidate) {
        double targetPe = sanitizePositive(target.fundamentals().pe());
        double candidatePe = sanitizePositive(candidate.fundamentals().pe());
        double peDistance = targetPe > 0.0 && candidatePe > 0.0
                ? Math.abs(Math.log(candidatePe / targetPe))
                : 2.0;
        double growthDistance = Math.abs(candidate.fundamentals().revenueGrowth() - target.fundamentals().revenueGrowth()) * 3.0;
        double fcfDistance = Math.abs(candidate.fundamentals().fcfMargin() - target.fundamentals().fcfMargin()) * 2.5;
        double roeDistance = Math.abs(candidate.fundamentals().roe() - target.fundamentals().roe()) * 1.8;
        return peDistance + growthDistance + fcfDistance + roeDistance;
    }

    private double sanitizePositive(double value) {
        return value > 0.0 ? value : 0.0;
    }

    private double peerDataCompleteness(
            boolean hasMarketCap,
            boolean hasRevenueGrowth,
            boolean hasFcfMargin,
            boolean hasRoic,
            boolean hasPe,
            boolean hasEvEbitda
    ) {
        int present = 0;
        present += hasMarketCap ? 1 : 0;
        present += hasRevenueGrowth ? 1 : 0;
        present += hasFcfMargin ? 1 : 0;
        present += hasRoic ? 1 : 0;
        present += hasPe ? 1 : 0;
        present += hasEvEbitda ? 1 : 0;
        return present / 6.0;
    }

    private MarketPeerItem toUsSnapshotPeerItem(
            StockSnapshot target,
            StockSnapshot candidate,
            UsStoredValuationSnapshotRecord storedSnapshot,
            UsMarketSnapshotRecord latestMarketSnapshot,
            UsFinancialDerivedMetricRecord latestDerived
    ) {
        QuickUsListValuation valuation = quickUsListValuation(candidate);
        Double currentPrice = storedSnapshot == null ? valuation.price() : MathSupport.round(storedSnapshot.currentPrice());
        Double fairValue = storedSnapshot == null ? valuation.fairValueMid() : MathSupport.round(storedSnapshot.fairValueMid());
        Double upside = storedSnapshot == null ? valuation.upside() : MathSupport.round(storedSnapshot.upsidePct());
        Double confidence = storedSnapshot == null ? MathSupport.round(valuation.confidence()) : MathSupport.round(storedSnapshot.confidenceLevel());
        Double qualityScore = storedSnapshot == null
                ? MathSupport.round(scoreFromRatio(Math.max(candidate.fundamentals().roe(), candidate.fundamentals().fcfMargin()), 60) / 100.0)
                : storedSnapshot.qualityScore() == null ? null : MathSupport.round(storedSnapshot.qualityScore());
        Double marketCap = latestMarketSnapshot == null || latestMarketSnapshot.marketCapVendor() == null
                ? null
                : MathSupport.round(latestMarketSnapshot.marketCapVendor().doubleValue());
        Double roic = latestDerived == null || latestDerived.roic() == null
                ? null
                : MathSupport.round(latestDerived.roic().doubleValue());
        String verdict = storedSnapshot == null
                ? rankingVerdict(upside == null ? 0.0 : upside)
                : normalizeVerdict(storedSnapshot.finalVerdict(), storedSnapshot.upsidePct());

        return new MarketPeerItem(
                candidate.symbol(),
                candidate.companyName(),
                candidate.industry(),
                sameIndustry(target, candidate) ? "industry" : "snapshot_fundamentals",
                currentPrice,
                fairValue,
                upside,
                MathSupport.round(candidate.fundamentals().pe()),
                MathSupport.round(candidate.fundamentals().evEbitda()),
                marketCap,
                MathSupport.round(candidate.fundamentals().revenueGrowth()),
                MathSupport.round(candidate.fundamentals().fcfMargin()),
                roic,
                confidence,
                qualityScore,
                qualityScore,
                MathSupport.round(peerDataCompleteness(
                        marketCap != null,
                        candidate.fundamentals().revenueGrowth() != 0.0,
                        candidate.fundamentals().fcfMargin() != 0.0,
                        roic != null,
                        candidate.fundamentals().pe() > 0.0,
                        candidate.fundamentals().evEbitda() > 0.0
                )),
                verdict
        );
    }

    private UsMarketSnapshotRecord latestMarketSnapshot(String ticker, UsStoredValuationSnapshotRecord storedSnapshot) {
        Long securityId = storedSnapshot == null
                ? usSecurityMasterService.resolveSecurityId(ticker).orElse(null)
                : Long.valueOf(storedSnapshot.securityId());
        if (securityId == null) {
            return null;
        }
        return marketSnapshotRepository.findLatestBySecurityId(securityId, 1).stream().findFirst().orElse(null);
    }

    private UsFinancialDerivedMetricRecord latestDerivedMetrics(String ticker, UsStoredValuationSnapshotRecord storedSnapshot) {
        Long securityId = storedSnapshot == null
                ? usSecurityMasterService.resolveSecurityId(ticker).orElse(null)
                : Long.valueOf(storedSnapshot.securityId());
        if (securityId == null) {
            return null;
        }
        return financialDerivedMetricsRepository.findLatestBySecurityId(securityId, 1).stream().findFirst().orElse(null);
    }

    private boolean sameIndustry(StockSnapshot left, StockSnapshot right) {
        return left.industry() != null
                && right.industry() != null
                && left.industry().equalsIgnoreCase(right.industry());
    }

    private String usPeerMatchBasis(UsSecurityMaster targetSecurity, UsRelativePeerComparable peer) {
        if (targetSecurity == null || peer == null) {
            return "peer_set";
        }
        if (sameValue(targetSecurity.industry(), peer.industry())) {
            return "industry";
        }
        if (sameValue(targetSecurity.sectorTemplate(), peer.sectorTemplate())) {
            return "sector_template";
        }
        if (sameValue(targetSecurity.companyType(), peer.companyType())) {
            return "company_type";
        }
        if (sameValue(targetSecurity.sector(), peer.sector())) {
            return "sector";
        }
        return "mixed";
    }

    private Map<String, Long> peerSelectionBreakdown(UsSecurityMaster targetSecurity, List<UsRelativePeerComparable> peers) {
        if (targetSecurity == null || peers.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> breakdown = new LinkedHashMap<>();
        for (UsRelativePeerComparable peer : peers) {
            breakdown.merge(usPeerMatchBasis(targetSecurity, peer), 1L, Long::sum);
        }
        return breakdown;
    }

    private boolean sameValue(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private Comparator<MarketRankingItem> rankingComparator(String rankingType) {
        Comparator<MarketRankingItem> base = Comparator
                .comparingDouble((MarketRankingItem item) -> nullableDouble(item.upsidePct()))
                .thenComparingDouble(item -> nullableDouble(item.confidenceScore()));
        return "overvalued".equals(rankingType) ? base : base.reversed();
    }

    private double nullableDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private String normalizeRankingType(String rankingType) {
        if ("overvalued".equalsIgnoreCase(rankingType)) {
            return "overvalued";
        }
        if ("quality".equalsIgnoreCase(rankingType)) {
            return "quality";
        }
        if ("value_trap".equalsIgnoreCase(rankingType)) {
            return "value_trap";
        }
        return "undervalued";
    }

    private Instant strictRankingFreshnessCutoff() {
        return Instant.now().minusSeconds(Math.max(strictFreshnessHours, 1) * 3600L);
    }

    private String rankingVerdict(double upside) {
        if (upside >= 0.05) {
            return "UNDERVALUED";
        }
        if (upside <= -0.05) {
            return "OVERVALUED";
        }
        return "FAIR";
    }

    private String normalizeVerdict(String rawVerdict, double upsideFallback) {
        if (rawVerdict == null || rawVerdict.isBlank()) {
            return rankingVerdict(upsideFallback);
        }
        String normalized = rawVerdict.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("UNDERVALUED") || normalized.contains("低估")) {
            return "UNDERVALUED";
        }
        if (normalized.contains("OVERVALUED") || normalized.contains("高估")) {
            return "OVERVALUED";
        }
        if (normalized.contains("FAIR") || normalized.contains("合理")) {
            return "FAIR";
        }
        return rankingVerdict(upsideFallback);
    }

    private String verdictLabel(double upside) {
        if (upside >= 0.20) {
            return "低估";
        }
        if (upside >= 0.05) {
            return "轻度低估";
        }
        if (upside <= -0.20) {
            return "高估";
        }
        if (upside <= -0.05) {
            return "轻度高估";
        }
        return "合理";
    }

    private String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }

    private int normalizeConfidence(double raw) {
        return raw <= 1.0 ? (int) Math.round(raw * 100.0) : (int) Math.round(raw);
    }

    private int scoreFromRatio(double ratio, int baseline) {
        double score = baseline + ratio * 130.0;
        return (int) Math.round(MathSupport.clamp(score, 45.0, 95.0));
    }

    private int scoreFromStored(Number ratio, int fallback) {
        if (ratio == null) {
            return fallback;
        }
        return scoreFromRatio(ratio.doubleValue(), 60);
    }

    private String gradeLabel(int score) {
        if (score >= 82) {
            return "优秀";
        }
        if (score >= 68) {
            return "良好";
        }
        return "估值合理";
    }

    private String analystRating(double upside, int confidenceScore) {
        if (upside >= 0.35 && confidenceScore >= 75) {
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

    private record QuickUsListValuation(
            double price,
            double fairValueMid,
            double fairValueLow,
            double fairValueHigh,
            double upside,
            double confidence
    ) {
    }
}
