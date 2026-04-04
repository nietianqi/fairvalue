package com.fairvalue.engine.us;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairvalue.engine.api.dto.us.UsDataQualityResponse;
import com.fairvalue.engine.api.dto.us.UsEquityProfileResponse;
import com.fairvalue.engine.api.dto.us.UsExplanationBlock;
import com.fairvalue.engine.api.dto.us.UsFairValueRange;
import com.fairvalue.engine.api.dto.us.UsFinancialQualityResponse;
import com.fairvalue.engine.api.dto.us.UsMethodOutput;
import com.fairvalue.engine.api.dto.us.UsRiskItem;
import com.fairvalue.engine.api.dto.us.UsScenarioOutput;
import com.fairvalue.engine.api.dto.us.UsValuationDecisionResponse;
import com.fairvalue.engine.api.dto.us.UsValuationExplanationResponse;
import com.fairvalue.engine.api.dto.us.UsValuationReportResponse;
import com.fairvalue.engine.api.dto.us.UsValuationRunRequest;
import com.fairvalue.engine.api.dto.us.UsValuationRunResponse;
import com.fairvalue.engine.api.dto.us.UsValuationSummaryResponse;
import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.domain.StockFundamentals;
import com.fairvalue.engine.domain.StockSnapshot;
import com.fairvalue.engine.repository.FinancialDerivedMetricsRepository;
import com.fairvalue.engine.repository.FinancialQualityScoresRepository;
import com.fairvalue.engine.repository.SourceDocumentRepository;
import com.fairvalue.engine.repository.SourceRegistryRepository;
import com.fairvalue.engine.service.MarketDataService;
import com.fairvalue.engine.service.ValuationService;
import com.fairvalue.engine.valuation.ValuationResult;
import com.fairvalue.engine.valuation.strategy.MathSupport;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

@Service
public class UsEquityValuationService {
    /** When true, all US valuations are treated as rankable regardless of price source.
     *  For local development only — never enable in production. */
    @org.springframework.beans.factory.annotation.Value("${app.valuation.us.dev-rankable-override:false}")
    private boolean devRankableOverride;

    private final MarketDataService marketDataService;
    private final ValuationService valuationService;
    private final UsSecClient usSecClient;
    private final UsLongbridgeClient usLongbridgeClient;
    private final UsStooqClient usStooqClient;
    private final UsSecurityMasterService usSecurityMasterService;
    private final UsSecurityClassificationService usSecurityClassificationService;
    private final UsDataQualityAuditService usDataQualityAuditService;
    private final UsConfiguredValuationModelsService usConfiguredValuationModelsService;
    private final UsValuationPersistenceService usValuationPersistenceService;
    private final UsValuationReadService usValuationReadService;
    private final FinancialDerivedMetricsRepository financialDerivedMetricsRepository;
    private final FinancialQualityScoresRepository financialQualityScoresRepository;
    private final SourceRegistryRepository sourceRegistryRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final ObjectMapper objectMapper;

    public UsEquityValuationService(
            MarketDataService marketDataService,
            ValuationService valuationService,
            UsSecClient usSecClient,
            UsLongbridgeClient usLongbridgeClient,
            UsStooqClient usStooqClient,
            UsSecurityMasterService usSecurityMasterService,
            UsSecurityClassificationService usSecurityClassificationService,
            UsDataQualityAuditService usDataQualityAuditService,
            UsConfiguredValuationModelsService usConfiguredValuationModelsService,
            UsValuationPersistenceService usValuationPersistenceService,
            UsValuationReadService usValuationReadService,
            FinancialDerivedMetricsRepository financialDerivedMetricsRepository,
            FinancialQualityScoresRepository financialQualityScoresRepository,
            SourceRegistryRepository sourceRegistryRepository,
            SourceDocumentRepository sourceDocumentRepository,
            ObjectMapper objectMapper
    ) {
        this.marketDataService = marketDataService;
        this.valuationService = valuationService;
        this.usSecClient = usSecClient;
        this.usLongbridgeClient = usLongbridgeClient;
        this.usStooqClient = usStooqClient;
        this.usSecurityMasterService = usSecurityMasterService;
        this.usSecurityClassificationService = usSecurityClassificationService;
        this.usDataQualityAuditService = usDataQualityAuditService;
        this.usConfiguredValuationModelsService = usConfiguredValuationModelsService;
        this.usValuationPersistenceService = usValuationPersistenceService;
        this.usValuationReadService = usValuationReadService;
        this.financialDerivedMetricsRepository = financialDerivedMetricsRepository;
        this.financialQualityScoresRepository = financialQualityScoresRepository;
        this.sourceRegistryRepository = sourceRegistryRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.objectMapper = objectMapper;
    }

    public UsEquityProfileResponse profile(String ticker) {
        StockSnapshot snapshot = usSnapshot(ticker);
        UsSecClient.UsSecProfile profile = usProfile(ticker);
        UsSecurityMaster classified = usSecurityClassificationService.ensureClassification(ticker, snapshot, profile);
        double dilutedShares = resolveDilutedShares(snapshot, profile);
        double marketCap = MathSupport.round(snapshot.price() * dilutedShares);

        return new UsEquityProfileResponse(
                snapshot.symbol(),
                snapshot.companyName(),
                firstNonBlank(classified.exchange(), profile == null ? null : profile.exchange(), inferExchange(snapshot.symbol())),
                firstNonBlank(classified.sector(), inferSector(snapshot.industry())),
                firstNonBlank(classified.industry(), snapshot.industry()),
                marketCap,
                MathSupport.round(dilutedShares),
                firstNonBlank(classified.companyType(), classifyCompany(snapshot)),
                firstNonBlank(classified.sectorTemplate(), sectorTemplate(snapshot)),
                MathSupport.round(snapshot.fundamentals().pe()),
                MathSupport.round(snapshot.fundamentals().evEbitda()),
                MathSupport.round(snapshot.fundamentals().revenueGrowth()),
                MathSupport.round(usDailyChange(ticker))
        );
    }

    public UsDataQualityResponse dataQuality(String ticker) {
        String normalizedTicker = normalizeTicker(ticker);
        try {
            UsDataQualityAuditRecord audit = usDataQualityAuditService.refreshForTicker(normalizedTicker);
            return new UsDataQualityResponse(
                    normalizedTicker,
                    audit.latest10kDate() == null ? null : audit.latest10kDate().toString(),
                    audit.latest10qDate() == null ? null : audit.latest10qDate().toString(),
                    audit.hasRecent8k(),
                    audit.shareCountVerified(),
                    audit.sbcQuantified(),
                    audit.guidanceStatus(),
                    safe(audit.confidenceLevel()),
                    parseStringList(audit.missingItemsJson()),
                    parseStringList(audit.warningFlagsJson())
            );
        } catch (Exception ignored) {
            return heuristicDataQuality(normalizedTicker);
        }
    }

    private UsDataQualityResponse heuristicDataQuality(String ticker) {
        StockSnapshot snapshot = usSnapshot(ticker);
        UsSecClient.UsSecProfile profile = usProfile(ticker);
        StockFundamentals f = snapshot.fundamentals();
        ValuationResult valuation = valuationService.valuate(Market.US, ticker);
        long securityId = resolveSecurityId(ticker);

        LocalDate latest10q = profile != null && profile.latest10qDate() != null
                ? profile.latest10qDate()
                : LocalDate.now().minusDays((long) f.dataFreshnessDays());
        LocalDate latest10k = profile != null && profile.latest10kDate() != null
                ? profile.latest10kDate()
                : latest10q.minusDays(280);

        List<String> missing = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (profile == null || profile.latest10qDate() == null) {
            missing.add("latest_quarter_missing_items");
        }
        if (profile == null || profile.sharesOutstanding() == null) {
            missing.add("share_count_not_verified");
        }
        if (profile == null || profile.annualRevenue() == null || profile.annualNetIncome() == null) {
            missing.add("core_financial_lines_incomplete");
        }
        if (f.analystCoverage() < 0.45) {
            missing.add("consensus_coverage_thin");
        }
        if ((profile == null || profile.stockBasedCompensation() == null) && f.sbcRatio() <= 0.0) {
            missing.add("sbc_disclosure_not_structured");
        }

        if (profile != null && profile.hasRecent8k()) {
            warnings.add("recent_8k_requires_review");
        }
        if (latest10q.isBefore(LocalDate.now().minusDays(130))) {
            warnings.add("quarterly_filing_stale");
        }
        if (f.sbcRatio() > 0.07) {
            warnings.add("high_sbc_ratio");
        }
        if (f.netCashToMarketCap() < -0.18) {
            warnings.add("balance_sheet_leverage");
        }
        if (f.earningsVolatility() > 0.38) {
            warnings.add("earnings_volatility_elevated");
        }
        if (valuation.confidence() < 0.65) {
            warnings.add("confidence_below_preferred_threshold");
        }

        boolean hasRecentCompanyIr = hasRecentCompanyIrGuidance(securityId);
        String guidance = hasRecentCompanyIr
                ? "company_ir_captured"
                : f.analystCoverage() > 0.65
                ? "captured"
                : profile != null ? "filings_only" : "guidance-blind";

        return new UsDataQualityResponse(
                snapshot.symbol(),
                latest10k.toString(),
                latest10q.toString(),
                profile != null && profile.hasRecent8k(),
                profile != null && profile.sharesOutstanding() != null,
                (profile != null && profile.stockBasedCompensation() != null) || f.sbcRatio() > 0.0,
                guidance,
                valuation.confidence(),
                missing,
                warnings
        );
    }

    public UsFinancialQualityResponse financialQuality(String ticker) {
        StockSnapshot snapshot = usSnapshot(ticker);
        UsSecClient.UsSecProfile profile = usProfile(ticker);
        StockFundamentals f = snapshot.fundamentals();
        double dilutedShares = resolveDilutedShares(snapshot, profile);
        long securityId = resolveSecurityId(ticker);
        UsFinancialQualityScoreRecord latestScore = financialQualityScoresRepository.findLatestAvailableBySecurityId(securityId).orElse(null);
        UsFinancialDerivedMetricRecord latestDerived = financialDerivedMetricsRepository.findLatestBySecurityId(securityId, 1)
                .stream()
                .findFirst()
                .orElse(null);

        if (latestScore != null) {
            Map<String, Double> profitability = new LinkedHashMap<>();
            profitability.put("revenue_growth", MathSupport.round(f.revenueGrowth()));
            profitability.put("fcf_margin", latestDerived != null && latestDerived.fcfMargin() != null
                    ? latestDerived.fcfMargin().doubleValue()
                    : MathSupport.round(f.fcfMargin()));
            profitability.put("roe", latestDerived != null && latestDerived.roe() != null
                    ? latestDerived.roe().doubleValue()
                    : MathSupport.round(f.roe()));
            profitability.put("buyback_yield", MathSupport.round(f.buybackYield()));
            profitability.put("dividend_yield", MathSupport.round(f.dividendYield()));

            double altmanZ = latestDerived != null && latestDerived.altmanZScore() != null
                    ? latestDerived.altmanZScore().doubleValue()
                    : MathSupport.round(MathSupport.clamp(1.7 + safe(latestScore.balanceSheetScore()) * 2.3 + safe(latestScore.earningsQualityScore()) * 1.1, 1.2, 5.2));
            double ownerEarnings = latestDerived != null && latestDerived.ownerEarningsEstimate() != null
                    ? latestDerived.ownerEarningsEstimate().doubleValue()
                    : MathSupport.round(estimateOwnerEarnings(snapshot, profile, dilutedShares));

            return new UsFinancialQualityResponse(
                    snapshot.symbol(),
                    profitability,
                    safe(latestScore.earningsQualityScore()),
                    safe(latestScore.revenueQualityScore()),
                    safe(latestScore.balanceSheetScore()),
                    safe(latestScore.capitalEfficiencyScore()),
                    safe(latestScore.capitalAllocationScore()),
                    safe(latestScore.totalQualityScore()),
                    parseStringList(latestScore.redFlagsJson()),
                    altmanZ,
                    ownerEarnings
            );
        }

        double earningsQuality = MathSupport.clamp(0.58 + f.fcfMargin() * 1.00 - f.sbcRatio() * 0.65 - f.earningsVolatility() * 0.40, 0.20, 0.95);
        double revenueQuality = MathSupport.clamp(0.52 + f.revenueGrowth() * 0.80 + f.analystCoverage() * 0.15, 0.20, 0.95);
        double balanceSheet = MathSupport.clamp(0.60 + f.netCashToMarketCap() * 0.65 - Math.max(0.0, -f.netCashToMarketCap()) * 0.40, 0.20, 0.95);
        double capitalEfficiency = MathSupport.clamp(0.45 + f.roe() * 1.10 + f.fcfMargin() * 0.45, 0.20, 0.95);
        double capitalAllocation = MathSupport.clamp(0.46 + f.buybackYield() * 2.0 + f.dividendYield() * 1.4 - f.sbcRatio() * 0.70, 0.20, 0.95);
        double total = (earningsQuality + revenueQuality + balanceSheet + capitalEfficiency + capitalAllocation) / 5.0;

        List<String> redFlags = new ArrayList<>();
        if (f.sbcRatio() > 0.08) {
            redFlags.add("sbc_dilution_high");
        }
        if (f.fcfMargin() < 0.05) {
            redFlags.add("weak_fcf_conversion");
        }
        if (f.netCashToMarketCap() < -0.15) {
            redFlags.add("balance_sheet_leverage");
        }
        if (profile != null && profile.annualNetIncome() != null && profile.annualNetIncome() <= 0) {
            redFlags.add("net_income_negative");
        }
        if (profile != null && profile.previousAnnualRevenue() != null && profile.annualRevenue() != null
                && profile.annualRevenue() < profile.previousAnnualRevenue()) {
            redFlags.add("revenue_contraction");
        }

        Map<String, Double> profitability = new LinkedHashMap<>();
        profitability.put("revenue_growth", MathSupport.round(f.revenueGrowth()));
        profitability.put("fcf_margin", MathSupport.round(f.fcfMargin()));
        profitability.put("roe", MathSupport.round(f.roe()));
        profitability.put("buyback_yield", MathSupport.round(f.buybackYield()));
        profitability.put("dividend_yield", MathSupport.round(f.dividendYield()));

        double altmanZ = MathSupport.round(MathSupport.clamp(1.7 + balanceSheet * 2.3 + earningsQuality * 1.1 + revenueQuality * 0.4, 1.2, 5.2));
        double ownerEarnings = MathSupport.round(estimateOwnerEarnings(snapshot, profile, dilutedShares));

        return new UsFinancialQualityResponse(
                snapshot.symbol(),
                profitability,
                MathSupport.round(earningsQuality),
                MathSupport.round(revenueQuality),
                MathSupport.round(balanceSheet),
                MathSupport.round(capitalEfficiency),
                MathSupport.round(capitalAllocation),
                MathSupport.round(total),
                redFlags,
                altmanZ,
                ownerEarnings
        );
    }

    public UsValuationRunResponse runValuation(String ticker, UsValuationRunRequest request) {
        return executeValuation(ticker, request, "manual", true).run();
    }

    public UsValuationRunResponse runScheduledValuation(String ticker) {
        return executeValuation(ticker, UsValuationRunRequest.defaults(), "scheduled", true).run();
    }

    private ComputedValuation executeValuation(String ticker, UsValuationRunRequest request, String runMode, boolean persist) {
        UsValuationRunRequest effective = normalize(request);
        StockSnapshot snapshot = usSnapshot(ticker);
        UsSecClient.UsSecProfile profile = usProfile(ticker);
        UsSecurityMaster classified = usSecurityClassificationService.ensureClassification(ticker, snapshot, profile);
        ValuationResult base = valuationService.valuate(Market.US, ticker);
        UsConfiguredValuationResult configured = usConfiguredValuationModelsService.evaluate(snapshot, classified, effective.customAssumptions());
        UsValuationModelContext context = configured.context();
        UsDataQualityResponse dataQuality = dataQuality(ticker);
        UsFinancialQualityResponse quality = financialQuality(ticker);
        UsEquityProfileResponse profileResponse = profile(ticker);
        PriceQualityAssessment priceQuality = assessPriceQuality(snapshot);

        List<UsConfiguredMethodValuation> selectedMethods = applyMethodSelection(configured.methods(), effective.forceMethods());
        selectedMethods = applyQualityGuardrails(selectedMethods, context, dataQuality, priceQuality);
        List<UsMethodOutput> methodOutputs = selectedMethods.stream()
                .map(method -> methodOutput(method, effective.style()))
                .toList();

        double blendedLow = methodOutputs.stream().mapToDouble(output -> output.bearValue() * output.weight()).sum();
        double blendedMid = methodOutputs.stream().mapToDouble(output -> output.baseValue() * output.weight()).sum();
        double blendedHigh = methodOutputs.stream().mapToDouble(output -> output.bullValue() * output.weight()).sum();
        UsFairValueRange range = new UsFairValueRange(
                MathSupport.round(blendedLow),
                MathSupport.round(blendedMid),
                MathSupport.round(blendedHigh)
        );

        UsRiskMatrixResult riskMatrixResult = configured.riskMatrixResult();
        List<UsRiskItem> riskMatrix = riskMatrixResult.items();
        double effectiveConfidence = MathSupport.round(MathSupport.clamp(
                Math.min(base.confidence(), configured.confidenceBase()),
                0.35,
                0.95
        ));
        if (isLowQualityInput(dataQuality, priceQuality)) {
            effectiveConfidence = MathSupport.round(Math.min(effectiveConfidence, 0.45));
        }
        List<UsScenarioOutput> scenarioMatrix = scenarioMatrix(base, range, effectiveConfidence, riskMatrixResult);

        double rawMarginOfSafety = (range.mid() - base.price()) / Math.max(range.mid(), 0.1);
        double marginOfSafety = MathSupport.round(rawMarginOfSafety - configured.requiredMarginOfSafety());
        String impliedExpectation = configured.reverseDcfAnalysis().impliedExpectationLabel();
        Map<String, Object> dataQualityAudit = buildDataQualityAudit(dataQuality);
        Map<String, Object> businessAndMoat = buildBusinessAndMoat(profileResponse, quality);
        Map<String, Object> financialScorecard = buildFinancialScorecard(quality);
        Map<String, String> actionable = buildActionableFramework();
        Map<String, Object> sourceAttribution = buildSourceAttribution(snapshot, context, selectedMethods, dataQuality, priceQuality);
        String uncertainty = buildUncertaintyAndErrorSources(snapshot, dataQuality, selectedMethods, priceQuality, context);
        Map<String, String> explanationBlocks = buildExplanationBlocks(
                snapshot,
                profileResponse,
                quality,
                dataQualityAudit,
                businessAndMoat,
                financialScorecard,
                sourceAttribution,
                selectedMethods,
                methodOutputs,
                scenarioMatrix,
                riskMatrix,
                range,
                rawMarginOfSafety,
                configured.requiredMarginOfSafety(),
                marginOfSafety,
                configured.modelSelectionReason(),
                impliedExpectation,
                actionable,
                uncertainty
        );
        UsValuationSummaryResponse summary = buildSummaryResponse(
                snapshot,
                base,
                range,
                effectiveConfidence,
                impliedExpectation,
                priceQuality
        );
        UsValuationDecisionResponse decision = buildDecisionResponse(
                snapshot,
                selectedMethods.stream().map(UsConfiguredMethodValuation::method).toList(),
                scenarioMatrix,
                riskMatrix,
                dataQualityAudit,
                sourceAttribution,
                range,
                effectiveConfidence,
                marginOfSafety,
                impliedExpectation,
                riskMatrixResult.valueTrapFlag()
        );
        UsValuationExplanationResponse explanation = buildExplanationResponse(explanationBlocks);
        UsValuationRunResponse runResponse = new UsValuationRunResponse(
                snapshot.symbol(),
                selectedMethods.stream().map(UsConfiguredMethodValuation::method).toList(),
                methodOutputs,
                scenarioMatrix,
                range.mid(),
                range,
                effectiveConfidence,
                marginOfSafety,
                impliedExpectation,
                riskMatrix,
                explanationBlocks,
                summary,
                decision,
                explanation
        );
        UsValuationReportResponse report = buildReportResponse(
                ticker,
                snapshot,
                runResponse,
                dataQuality,
                quality,
                profileResponse,
                dataQualityAudit
        );
        if (persist) {
            usValuationPersistenceService.persistRun(
                    ticker,
                    effective,
                    snapshot,
                    base,
                    selectedMethods,
                    methodOutputs,
                    scenarioMatrix,
                    riskMatrix,
                    explanationBlocks,
                    range,
                    range.mid(),
                    effectiveConfidence,
                    marginOfSafety,
                    configured.reverseDcfAnalysis(),
                    summary.verdict(),
                    classified,
                    runMode,
                    summary,
                    decision,
                    explanation,
                    report,
                    quality.totalQualityScore(),
                    dataQuality.confidenceLevel()
            );
        }
        return new ComputedValuation(runResponse, report);
    }

    public UsValuationSummaryResponse summary(String ticker) {
        String normalizedTicker = normalizeTicker(ticker);
        UsStoredValuationSnapshotRecord stored = usValuationReadService.findLatestSnapshot(normalizedTicker).orElse(null);
        if (shouldRefreshStoredSnapshot(normalizedTicker, stored)) {
            return executeValuation(
                    normalizedTicker,
                    UsValuationRunRequest.defaults(),
                    "api",
                    true
            ).run().summary();
        }
        return usValuationReadService.findLatestSummary(normalizedTicker)
                .orElseGet(() -> executeValuation(
                        normalizedTicker,
                        UsValuationRunRequest.defaults(),
                        "api",
                        true
                ).run().summary());
    }

    public UsValuationReportResponse report(String ticker) {
        String normalizedTicker = normalizeTicker(ticker);
        UsStoredValuationSnapshotRecord stored = usValuationReadService.findLatestSnapshot(normalizedTicker).orElse(null);
        if (shouldRefreshStoredSnapshot(normalizedTicker, stored)) {
            return executeValuation(
                    normalizedTicker,
                    UsValuationRunRequest.defaults(),
                    "api",
                    true
            ).report();
        }
        return usValuationReadService.findLatestReport(normalizedTicker)
                .orElseGet(() -> executeValuation(
                        normalizedTicker,
                        UsValuationRunRequest.defaults(),
                        "api",
                        true
                ).report());
    }

    private UsValuationReportResponse buildReportResponse(
            String ticker,
            StockSnapshot snapshot,
            UsValuationRunResponse run,
            UsDataQualityResponse dataQuality,
            UsFinancialQualityResponse quality,
            UsEquityProfileResponse profileResponse,
            Map<String, Object> dataQualityAudit
    ) {
        Map<String, Object> businessAndMoat = buildBusinessAndMoat(profileResponse, quality);
        Map<String, Object> financialScorecard = buildFinancialScorecard(quality);
        Map<String, Object> usMarketModifiers = buildMarketModifiers(snapshot, run, run.decision().sourceAttribution());
        Map<String, String> actionable = buildActionableFramework();
        String uncertainty = buildUncertaintyAndErrorSources(
                snapshot,
                dataQuality,
                List.of(),
                assessPriceQuality(snapshot),
                null
        );

        return new UsValuationReportResponse(
                ticker.toUpperCase(Locale.ROOT),
                run.summary().verdict(),
                "US valuation combines cash-flow, expectation and relative anchors with explicit scenario probabilities.",
                dataQualityAudit,
                businessAndMoat,
                financialScorecard,
                usMarketModifiers,
                run.riskMatrix(),
                run.methodOutputs(),
                run.scenarioMatrix(),
                run.fairValueRange(),
                actionable,
                uncertainty,
                run.summary(),
                run.decision(),
                run.explanation()
        );
    }

    private List<UsScenarioOutput> scenarioMatrix(
            ValuationResult base,
            UsFairValueRange range,
            double effectiveConfidence,
            UsRiskMatrixResult riskMatrixResult
    ) {
        double bear = MathSupport.clamp(
                0.24 + riskMatrixResult.bearProbabilityDelta() + (1.0 - effectiveConfidence) * 0.05,
                0.18,
                0.55
        );
        double bull = MathSupport.clamp(
                0.18 + riskMatrixResult.bullProbabilityDelta() + effectiveConfidence * 0.03,
                0.08,
                0.30
        );
        if (riskMatrixResult.valueTrapFlag()) {
            bear = MathSupport.clamp(bear + 0.03, 0.18, 0.58);
            bull = MathSupport.clamp(bull - 0.02, 0.06, 0.30);
        }
        double baseProb = MathSupport.round(Math.max(1.0 - bear - bull, 0.18));
        double normalizedTotal = bear + bull + baseProb;
        bear = MathSupport.round(bear / normalizedTotal);
        bull = MathSupport.round(bull / normalizedTotal);
        baseProb = MathSupport.round(1.0 - bear - bull);

        return List.of(
                new UsScenarioOutput("bear", bear, range.low(), MathSupport.round(range.low() * 0.90), MathSupport.round(range.low() * 1.08), MathSupport.round(range.low() / base.price() - 1.0)),
                new UsScenarioOutput("base", baseProb, range.mid(), range.low(), range.high(), MathSupport.round(range.mid() / base.price() - 1.0)),
                new UsScenarioOutput("bull", bull, range.high(), MathSupport.round(range.mid() * 0.95), MathSupport.round(range.high() * 1.12), MathSupport.round(range.high() / base.price() - 1.0))
        );
    }

    private UsValuationSummaryResponse buildSummaryResponse(
            StockSnapshot snapshot,
            ValuationResult base,
            UsFairValueRange range,
            double confidenceLevel,
            String impliedExpectation,
            PriceQualityAssessment priceQuality
    ) {
        UsFairValueRange buyZone = new UsFairValueRange(
                MathSupport.round(range.low() * 0.85),
                MathSupport.round(range.low()),
                MathSupport.round(range.mid() * 0.92)
        );
        UsFairValueRange holdZone = new UsFairValueRange(
                MathSupport.round(range.low()),
                MathSupport.round(range.mid()),
                MathSupport.round(range.high())
        );
        UsFairValueRange avoidZone = new UsFairValueRange(
                MathSupport.round(range.high()),
                MathSupport.round(range.high() * 1.08),
                MathSupport.round(range.high() * 1.18)
        );
        return new UsValuationSummaryResponse(
                snapshot.symbol(),
                verdict(base, priceQuality),
                range,
                assessment(base, priceQuality),
                base.upside(),
                confidenceLevel,
                buyZone,
                holdZone,
                avoidZone,
                snapshot.price(),
                snapshot.dataVersion(),
                impliedExpectation,
                priceQuality.priceAsOf(),
                priceQuality.priceFreshnessDays(),
                priceQuality.priceSourceType(),
                priceQuality.rankable(),
                priceQuality.valuationStatus(),
                priceQuality.exclusionReason()
        );
    }

    private UsValuationDecisionResponse buildDecisionResponse(
            StockSnapshot snapshot,
            List<String> valuationMethods,
            List<UsScenarioOutput> scenarioMatrix,
            List<UsRiskItem> riskMatrix,
            Map<String, Object> dataQualityAudit,
            Map<String, Object> sourceAttribution,
            UsFairValueRange range,
            double confidenceLevel,
            double marginOfSafety,
            String impliedExpectation,
            boolean valueTrapFlag
    ) {
        return new UsValuationDecisionResponse(
                snapshot.symbol(),
                snapshot.price(),
                range,
                confidenceLevel,
                marginOfSafety,
                impliedExpectation,
                valueTrapFlag,
                valuationMethods,
                scenarioMatrix,
                riskMatrix,
                dataQualityAudit,
                sourceAttribution
        );
    }

    private UsValuationExplanationResponse buildExplanationResponse(Map<String, String> explanationBlocks) {
        List<UsExplanationBlock> blocks = new ArrayList<>();
        int order = 1;
        for (String key : explanationOrder()) {
            blocks.add(new UsExplanationBlock(
                    key,
                    explanationTitle(key),
                    explanationBlocks.getOrDefault(key, ""),
                    order
            ));
            order += 1;
        }
        return new UsValuationExplanationResponse(blocks);
    }

    private Map<String, String> buildExplanationBlocks(
            StockSnapshot snapshot,
            UsEquityProfileResponse profile,
            UsFinancialQualityResponse quality,
            Map<String, Object> dataQualityAudit,
            Map<String, Object> businessAndMoat,
            Map<String, Object> financialScorecard,
            Map<String, Object> sourceAttribution,
            List<UsConfiguredMethodValuation> selectedMethods,
            List<UsMethodOutput> methodOutputs,
            List<UsScenarioOutput> scenarioMatrix,
            List<UsRiskItem> riskMatrix,
            UsFairValueRange fairValueRange,
            double rawMarginOfSafety,
            double requiredMarginOfSafety,
            double effectiveMarginOfSafety,
            String modelSelectionReason,
            String impliedExpectation,
            Map<String, String> actionableFramework,
            String uncertainty
    ) {
        Map<String, String> blocks = new LinkedHashMap<>();
        blocks.put("one_line_verdict", buildOneLineVerdict(snapshot, fairValueRange, impliedExpectation));
        blocks.put("executive_summary", "Current price " + MathSupport.round(snapshot.price())
                + " versus fair value range " + fairValueRange.low() + " - " + fairValueRange.high()
                + ". The engine blends FCFF, historical multiples, relative valuation, and reverse DCF with explicit risk adjustments.");
        blocks.put("data_quality_audit", asText(dataQualityAudit));
        blocks.put("business_and_moat", asText(businessAndMoat));
        blocks.put("financial_quality_scorecard", asText(financialScorecard));
        blocks.put("growth_and_catalysts", "Revenue growth="
                + MathSupport.round(quality.profitability5yTtm().getOrDefault("revenue_growth", 0.0))
                + ", FCF margin="
                + MathSupport.round(quality.profitability5yTtm().getOrDefault("fcf_margin", 0.0))
                + ", implied expectation="
                + impliedExpectation
                + ", source attribution="
                + asText(sourceAttribution));
        blocks.put("risk_matrix", riskSummary(riskMatrix));
        blocks.put("valuation_breakdown", valuationBreakdownSummary(methodOutputs, selectedMethods, sourceAttribution, modelSelectionReason));
        blocks.put("scenario_matrix", scenarioSummary(scenarioMatrix));
        blocks.put("final_fair_value", "Current price="
                + MathSupport.round(snapshot.price())
                + ", fair value range="
                + fairValueRange.low()
                + " / "
                + fairValueRange.mid()
                + " / "
                + fairValueRange.high()
                + ", raw margin="
                + MathSupport.round(rawMarginOfSafety)
                + ", required margin="
                + MathSupport.round(requiredMarginOfSafety)
                + ", effective margin="
                + MathSupport.round(effectiveMarginOfSafety));
        blocks.put("actionable_framework", asText(actionableFramework));
        blocks.put("uncertainty_and_error_sources", uncertainty);
        return blocks;
    }

    private Map<String, Object> buildDataQualityAudit(UsDataQualityResponse dataQuality) {
        Map<String, Object> dataQualityAudit = new LinkedHashMap<>();
        dataQualityAudit.put("latest_10k_date", dataQuality.latest10kDate());
        dataQualityAudit.put("latest_10q_date", dataQuality.latest10qDate());
        dataQualityAudit.put("share_count_verified", dataQuality.shareCountVerified());
        dataQualityAudit.put("sbc_quantified", dataQuality.sbcQuantified());
        dataQualityAudit.put("confidence_level", dataQuality.confidenceLevel());
        dataQualityAudit.put("guidance_status", dataQuality.guidanceStatus());
        dataQualityAudit.put("missing_items", dataQuality.missingItems());
        dataQualityAudit.put("warning_flags", dataQuality.warningFlags());
        return dataQualityAudit;
    }

    private Map<String, Object> buildBusinessAndMoat(UsEquityProfileResponse profile, UsFinancialQualityResponse quality) {
        Map<String, Object> businessAndMoat = new LinkedHashMap<>();
        double moatScore = MathSupport.round(MathSupport.clamp(
                0.45 + quality.revenueQualityScore() * 0.30 + quality.capitalEfficiencyScore() * 0.25,
                0.20,
                0.95
        ));
        businessAndMoat.put("company_type", profile.companyType());
        businessAndMoat.put("sector_template", profile.sectorTemplate());
        businessAndMoat.put("moat_score", moatScore);
        businessAndMoat.put("moat_level", moatScore > 0.75 ? "Wide" : moatScore > 0.58 ? "Narrow" : "None");
        return businessAndMoat;
    }

    private Map<String, Object> buildFinancialScorecard(UsFinancialQualityResponse quality) {
        Map<String, Object> financialScorecard = new LinkedHashMap<>();
        financialScorecard.put("total_quality_score", quality.totalQualityScore());
        financialScorecard.put("quality_breakdown", Map.of(
                "earnings", quality.earningsQualityScore(),
                "revenue", quality.revenueQualityScore(),
                "balance_sheet", quality.balanceSheetScore(),
                "capital_efficiency", quality.capitalEfficiencyScore(),
                "capital_allocation", quality.capitalAllocationScore()
        ));
        financialScorecard.put("red_flags", quality.redFlags());
        financialScorecard.put("altman_z_score", quality.altmanZ());
        financialScorecard.put("owner_earnings_estimate", quality.ownerEarnings());
        return financialScorecard;
    }

    private Map<String, Object> buildMarketModifiers(
            StockSnapshot snapshot,
            UsValuationRunResponse run,
            Map<String, Object> sourceAttribution
    ) {
        Map<String, Object> usMarketModifiers = new LinkedHashMap<>();
        usMarketModifiers.put("sbc_ratio", MathSupport.round(snapshot.fundamentals().sbcRatio()));
        usMarketModifiers.put("buyback_yield", MathSupport.round(snapshot.fundamentals().buybackYield()));
        usMarketModifiers.put("analyst_coverage", MathSupport.round(snapshot.fundamentals().analystCoverage()));
        usMarketModifiers.put("implied_expectation", run.impliedExpectation());
        usMarketModifiers.put("data_version", snapshot.dataVersion());
        usMarketModifiers.put("source_attribution", sourceAttribution);
        return usMarketModifiers;
    }

    private Map<String, String> buildActionableFramework() {
        Map<String, String> actionable = new LinkedHashMap<>();
        actionable.put("buy", "Consider adding only inside buy-zone and when risk matrix has no new High/High item.");
        actionable.put("hold", "Hold when price stays in hold-zone and thesis quality metrics remain stable.");
        actionable.put("reduce", "Reduce when price enters avoid-zone or expectation label stays aggressive without catalyst support.");
        return actionable;
    }

    private Map<String, Object> buildSourceAttribution(
            StockSnapshot snapshot,
            UsValuationModelContext context,
            List<UsConfiguredMethodValuation> selectedMethods,
            UsDataQualityResponse dataQuality,
            PriceQualityAssessment priceQuality
    ) {
        String dataVersion = firstNonBlank(snapshot.dataVersion(), "unknown");
        String priceSource = parsePriceSource(dataVersion);
        Map<String, Object> attribution = new LinkedHashMap<>();
        attribution.put("data_version", dataVersion);
        attribution.put("price_source", priceSource);
        attribution.put("price_source_type", priceQuality.priceSourceType());
        attribution.put("price_as_of", priceQuality.priceAsOf());
        attribution.put("price_freshness_days", priceQuality.priceFreshnessDays());
        attribution.put("rankable", priceQuality.rankable());
        attribution.put("valuation_status", priceQuality.valuationStatus());
        attribution.put("exclusion_reason", priceQuality.exclusionReason());
        attribution.put("guidance_status", dataQuality.guidanceStatus());
        UsExternalValuationParameterService.ExternalParameterSnapshot external = context == null
                ? null
                : context.externalParameterSnapshot();
        String industryMatchSource = external == null ? "template_fallback" : external.industryMatchSource();
        Double industryMatchConfidence = external == null ? 0.0 : external.industryMatchConfidence();
        boolean industryFallbackUsed = external == null || external.industryFallbackUsed();

        Map<String, String> parameterSources = new LinkedHashMap<>();
        List<String> peerSetTickers = new ArrayList<>();
        Map<String, Long> peerSelectionBreakdown = new LinkedHashMap<>();
        List<String> peerFilterMetrics = new ArrayList<>();
        String peerSetSource = null;
        String peerSelectionBasis = null;
        String relativeSourceMode = null;
        String peerFilterSummary = null;
        String peerSelectionRuleVersion = null;
        Integer peerCandidateCount = null;
        Map<String, String> effectiveTargetMultipleSources = new LinkedHashMap<>();
        for (UsConfiguredMethodValuation method : selectedMethods) {
            if (method.assumptionsJson() == null || method.assumptionsJson().isBlank()) {
                continue;
            }
            try {
                Map<String, Object> assumptions = objectMapper.readValue(method.assumptionsJson(), new TypeReference<Map<String, Object>>() { });
                Object rawSources = assumptions.get("parameter_sources");
                if (rawSources instanceof Map<?, ?> sourcesMap) {
                    for (Map.Entry<?, ?> entry : sourcesMap.entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null) {
                            parameterSources.put(entry.getKey().toString(), entry.getValue().toString());
                        }
                    }
                }
                Object rawPeerTickers = assumptions.get("peer_set_tickers");
                if (rawPeerTickers instanceof List<?> peerTickerList) {
                    for (Object value : peerTickerList) {
                        if (value != null) {
                            String ticker = value.toString();
                            if (!ticker.isBlank() && !peerSetTickers.contains(ticker)) {
                                peerSetTickers.add(ticker);
                            }
                        }
                    }
                }
                Object rawPeerSelectionBreakdown = assumptions.get("peer_selection_breakdown");
                if (rawPeerSelectionBreakdown instanceof Map<?, ?> breakdownMap) {
                    for (Map.Entry<?, ?> entry : breakdownMap.entrySet()) {
                        if (entry.getKey() == null || entry.getValue() == null) {
                            continue;
                        }
                        Long count = coerceLong(entry.getValue());
                        if (count != null && count > 0) {
                            peerSelectionBreakdown.put(entry.getKey().toString(), count);
                        }
                    }
                }
                Object rawPeerFilterMetrics = assumptions.get("peer_filter_metrics");
                if (rawPeerFilterMetrics instanceof List<?> peerFilterMetricList) {
                    for (Object value : peerFilterMetricList) {
                        if (value != null) {
                            String metric = value.toString();
                            if (!metric.isBlank() && !peerFilterMetrics.contains(metric)) {
                                peerFilterMetrics.add(metric);
                            }
                        }
                    }
                }
                if (peerSetSource == null && assumptions.get("peer_set_source") != null) {
                    peerSetSource = assumptions.get("peer_set_source").toString();
                }
                if (peerSelectionBasis == null && assumptions.get("peer_selection_basis") != null) {
                    peerSelectionBasis = assumptions.get("peer_selection_basis").toString();
                }
                if (relativeSourceMode == null && assumptions.get("relative_source_mode") != null) {
                    relativeSourceMode = assumptions.get("relative_source_mode").toString();
                }
                if (peerFilterSummary == null && assumptions.get("peer_filter_summary") != null) {
                    peerFilterSummary = assumptions.get("peer_filter_summary").toString();
                }
                if (peerSelectionRuleVersion == null && assumptions.get("peer_selection_rule_version") != null) {
                    peerSelectionRuleVersion = assumptions.get("peer_selection_rule_version").toString();
                }
                if (peerCandidateCount == null && assumptions.get("peer_candidate_count") instanceof Number number) {
                    peerCandidateCount = number.intValue();
                }
                captureStringMapEntry(assumptions, "effective_target_ev_ebitda_source", effectiveTargetMultipleSources, "target_ev_ebitda");
                captureStringMapEntry(assumptions, "effective_target_pe_source", effectiveTargetMultipleSources, "target_pe");
            } catch (Exception ignored) {
                // Keep source attribution best-effort; it must not break valuation output.
            }
        }
        String erpSource = firstNonBlank(
                parameterSources.get("wacc_erp"),
                resolveConfiguredTemplateErpSource(parameterSources),
                "configured_template"
        );
        parameterSources.putIfAbsent("wacc_erp", erpSource);
        attribution.put("parameter_sources", parameterSources);
        attribution.put("macro_sources", parameterSources.values().stream().distinct().toList());
        attribution.put("risk_free_rate_source", parameterSources.get("wacc_rf"));
        attribution.put("erp_source", erpSource);
        attribution.put("beta_source", parameterSources.get("wacc_beta"));
        attribution.put("industry_multiple_source", firstNonBlank(
                parameterSources.get("target_ev_ebitda"),
                parameterSources.get("target_pe")
        ));
        Map<String, String> targetMultipleSources = new LinkedHashMap<>();
        targetMultipleSources.put("target_ev_ebitda", parameterSources.get("target_ev_ebitda"));
        targetMultipleSources.put("target_pe", parameterSources.get("target_pe"));
        attribution.put("target_multiple_sources", targetMultipleSources);
        attribution.put("market_multiple_source", "market_snapshot");
        boolean templateOnlyRelative = "template_only".equalsIgnoreCase(relativeSourceMode) || peerSetTickers.isEmpty();
        attribution.put("peer_set_source", templateOnlyRelative ? "template_only" : firstNonBlank(peerSetSource, "peer_set"));
        attribution.put("peer_selection_basis", templateOnlyRelative ? "template_only" : firstNonBlank(peerSelectionBasis, "mixed"));
        attribution.put("relative_source_mode", templateOnlyRelative ? "template_only" : firstNonBlank(relativeSourceMode, "peer_set_plus_template"));
        attribution.put("peer_selection_breakdown", peerSelectionBreakdown);
        attribution.put("peer_candidate_count", peerCandidateCount);
        attribution.put("peer_selection_rule_version", firstNonBlank(peerSelectionRuleVersion, templateOnlyRelative ? null : "v2_strict"));
        attribution.put("peer_filter_summary", peerFilterSummary);
        attribution.put("peer_filter_metrics", peerFilterMetrics);
        attribution.put("effective_target_multiple_sources", effectiveTargetMultipleSources);
        attribution.put("peer_set_tickers", peerSetTickers);
        attribution.put("industry_match_source", industryMatchSource);
        attribution.put("industry_match_confidence", industryMatchConfidence);
        attribution.put("industry_fallback_used", industryFallbackUsed);
        attribution.put("industryMatchSource", industryMatchSource);
        attribution.put("industryMatchConfidence", industryMatchConfidence);
        attribution.put("industryFallbackUsed", industryFallbackUsed);
        attribution.put("source_attribution_version", "v2");
        return attribution;
    }

    private Map<String, Object> buildSourceAttribution(
            StockSnapshot snapshot,
            List<UsConfiguredMethodValuation> selectedMethods,
            UsDataQualityResponse dataQuality
    ) {
        return buildSourceAttribution(
                snapshot,
                null,
                selectedMethods,
                dataQuality,
                assessPriceQuality(snapshot)
        );
    }

    private String resolveConfiguredTemplateErpSource(Map<String, String> parameterSources) {
        if (parameterSources == null || parameterSources.isEmpty()) {
            return null;
        }
        boolean hasDamodaranReference = parameterSources.values().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains("damodaran"));
        return hasDamodaranReference ? "configured_template:damodaran_ref" : "configured_template";
    }

    private String buildUncertaintyAndErrorSources(
            StockSnapshot snapshot,
            UsDataQualityResponse dataQuality,
            List<UsConfiguredMethodValuation> selectedMethods,
            PriceQualityAssessment priceQuality,
            UsValuationModelContext context
    ) {
        List<String> notes = new ArrayList<>();
        String dataVersion = firstNonBlank(snapshot.dataVersion(), "").toLowerCase(Locale.ROOT);
        notes.add("price data version=" + firstNonBlank(snapshot.dataVersion(), "unknown"));
        if ("research_fallback".equals(priceQuality.priceSourceType())) {
            notes.add("Price layer is on research fallback and is excluded from tradable rankings.");
        }
        if (priceQuality.priceFreshnessDays() != null && priceQuality.priceFreshnessDays() > 3) {
            notes.add("Price freshness exceeds strict ranking threshold at " + priceQuality.priceFreshnessDays() + " days.");
        }
        if (!dataQuality.warningFlags().isEmpty()) {
            notes.add("data quality warnings=" + String.join(", ", dataQuality.warningFlags()));
        }
        if (!dataQuality.missingItems().isEmpty()) {
            notes.add("missing items=" + String.join(", ", dataQuality.missingItems()));
        }
        long sparseMethods = selectedMethods.stream()
                .filter(method -> method.notes() != null && method.notes().toLowerCase(Locale.ROOT).contains("sparse"))
                .count();
        if (sparseMethods > 0) {
            notes.add("historical/relative market samples remain sparse for part of the method set.");
        }
        if (selectedMethods.stream().anyMatch(UsConfiguredMethodValuation::outlierTrimmed)) {
            notes.add("DCF outlier guardrail trimmed an extreme method output.");
        }
        if (context != null
                && context.externalParameterSnapshot() != null
                && context.externalParameterSnapshot().industryFallbackUsed()) {
            notes.add("Industry multiple match fell back from the preferred Damodaran alias chain.");
        }
        return String.join(" ", notes);
    }

    private String buildOneLineVerdict(StockSnapshot snapshot, UsFairValueRange fairValueRange, String impliedExpectation) {
        double upside = fairValueRange.mid() / Math.max(snapshot.price(), 0.1) - 1.0;
        if (upside >= 0.20) {
            return "Undervalued with margin and manageable expectations.";
        }
        if (upside >= 0.05) {
            return "Slightly undervalued, but still dependent on execution.";
        }
        if (upside <= -0.20) {
            return "Overvalued with aggressive expectations already embedded.";
        }
        if (upside <= -0.05) {
            return "Slightly overvalued with limited margin of safety.";
        }
        return "Near fair value, with expectation label=" + impliedExpectation + ".";
    }

    private String riskSummary(List<UsRiskItem> riskMatrix) {
        return riskMatrix.stream()
                .map(item -> item.riskType() + "=" + item.probability() + "/" + item.impact() + " -> " + item.adjustmentType() + ":" + item.adjustment())
                .reduce((left, right) -> left + "; " + right)
                .orElse("No structured risk items generated.");
    }

    private String scenarioSummary(List<UsScenarioOutput> scenarioMatrix) {
        return scenarioMatrix.stream()
                .map(item -> item.scenario() + "=" + item.probability() + "@" + item.targetPrice())
                .reduce((left, right) -> left + "; " + right)
                .orElse("No scenario outputs generated.");
    }

    private String valuationBreakdownSummary(
            List<UsMethodOutput> methodOutputs,
            List<UsConfiguredMethodValuation> selectedMethods,
            Map<String, Object> sourceAttribution,
            String modelSelectionReason
    ) {
        String methods = methodOutputs.stream()
                .map(output -> output.method() + "=" + output.baseValue() + "x" + output.weight()
                        + "[" + firstNonBlank(output.methodStatus(), "active")
                        + (output.outlierTrimmed() ? ",trimmed" : "")
                        + (output.weightAdjustedByDataQuality() ? ",quality-adjusted" : "")
                        + "]")
                .reduce((left, right) -> left + "; " + right)
                .orElse("No methods selected.");
        return modelSelectionReason + " Methods: " + methods + ". Sources: " + asText(sourceAttribution);
    }

    private List<String> explanationOrder() {
        return List.of(
                "one_line_verdict",
                "executive_summary",
                "data_quality_audit",
                "business_and_moat",
                "financial_quality_scorecard",
                "growth_and_catalysts",
                "risk_matrix",
                "valuation_breakdown",
                "scenario_matrix",
                "final_fair_value",
                "actionable_framework",
                "uncertainty_and_error_sources"
        );
    }

    private String explanationTitle(String key) {
        return switch (key) {
            case "one_line_verdict" -> "One-line Verdict";
            case "executive_summary" -> "Executive Summary";
            case "data_quality_audit" -> "Data Quality Audit";
            case "business_and_moat" -> "Business And Moat";
            case "financial_quality_scorecard" -> "Financial Quality Scorecard";
            case "growth_and_catalysts" -> "Growth And Catalysts";
            case "risk_matrix" -> "Risk Matrix";
            case "valuation_breakdown" -> "Valuation Breakdown";
            case "scenario_matrix" -> "Scenario Matrix";
            case "final_fair_value" -> "Final Fair Value";
            case "actionable_framework" -> "Actionable Framework";
            case "uncertainty_and_error_sources" -> "Uncertainty And Error Sources";
            default -> key;
        };
    }

    private String asText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return String.valueOf(value);
        }
    }

    private UsMethodOutput methodOutput(UsConfiguredMethodValuation method, String style) {
        double styleFactor = switch (style.toLowerCase(Locale.ROOT)) {
            case "conservative" -> 0.97;
            case "aggressive" -> 1.03;
            default -> 1.0;
        };
        return new UsMethodOutput(
                method.method(),
                MathSupport.round(method.bearValue() * styleFactor),
                MathSupport.round(method.baseValue() * styleFactor),
                MathSupport.round(method.bullValue() * styleFactor),
                method.weight(),
                method.rationale(),
                method.methodStatus(),
                method.outlierTrimmed(),
                method.weightAdjustedByDataQuality()
        );
    }

    private List<UsConfiguredMethodValuation> applyMethodSelection(List<UsConfiguredMethodValuation> methods, List<String> forceMethods) {
        if (forceMethods == null || forceMethods.isEmpty()) {
            return normalizeMethodWeights(methods);
        }

        Set<String> target = forceMethods.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        target.add("reverse_dcf");

        List<UsConfiguredMethodValuation> filtered = methods.stream()
                .filter(method -> target.contains(method.method().toLowerCase(Locale.ROOT)))
                .toList();

        if (filtered.size() < 3) {
            return normalizeMethodWeights(methods);
        }

        return normalizeMethodWeights(filtered);
    }

    private List<UsConfiguredMethodValuation> normalizeMethodWeights(List<UsConfiguredMethodValuation> methods) {
        double weightSum = methods.stream().mapToDouble(UsConfiguredMethodValuation::weight).sum();
        if (weightSum <= 0.0) {
            double equalWeight = 1.0 / Math.max(methods.size(), 1);
            return methods.stream()
                    .map(method -> new UsConfiguredMethodValuation(
                            method.method(),
                            method.bearValue(),
                            method.baseValue(),
                            method.bullValue(),
                            MathSupport.round(equalWeight),
                            method.rationale(),
                            method.inputSnapshotDate(),
                            method.primaryMethod(),
                            method.assumptionsJson(),
                            method.sensitivityJson(),
                            method.notes(),
                            method.methodStatus(),
                            method.outlierTrimmed(),
                            method.weightAdjustedByDataQuality()
                    ))
                    .toList();
        }
        return methods.stream()
                .map(method -> new UsConfiguredMethodValuation(
                        method.method(),
                        method.bearValue(),
                        method.baseValue(),
                        method.bullValue(),
                        MathSupport.round(method.weight() / weightSum),
                        method.rationale(),
                        method.inputSnapshotDate(),
                        method.primaryMethod(),
                        method.assumptionsJson(),
                        method.sensitivityJson(),
                        method.notes(),
                        method.methodStatus(),
                        method.outlierTrimmed(),
                        method.weightAdjustedByDataQuality()
                ))
                .toList();
    }

    private List<UsConfiguredMethodValuation> applyQualityGuardrails(
            List<UsConfiguredMethodValuation> methods,
            UsValuationModelContext context,
            UsDataQualityResponse dataQuality,
            PriceQualityAssessment priceQuality
    ) {
        if (methods == null || methods.isEmpty()) {
            return List.of();
        }
        List<UsConfiguredMethodValuation> adjusted = new ArrayList<>(normalizeMethodWeights(methods));
        boolean lowQualityInput = isLowQualityInput(dataQuality, priceQuality);
        if (lowQualityInput) {
            adjusted = capDcfWeight(adjusted, 0.15);
        }
        if (context != null
                && context.security() != null
                && "us_managed_care".equalsIgnoreCase(context.security().sectorTemplate())) {
            adjusted = trimManagedCareDcfOutlier(adjusted);
        }
        return normalizeMethodWeights(adjusted);
    }

    private boolean isLowQualityInput(UsDataQualityResponse dataQuality, PriceQualityAssessment priceQuality) {
        return dataQuality.latest10kDate() == null
                || dataQuality.latest10kDate().isBlank()
                || dataQuality.latest10qDate() == null
                || dataQuality.latest10qDate().isBlank()
                || !dataQuality.shareCountVerified()
                || "guidance_blind".equalsIgnoreCase(dataQuality.guidanceStatus())
                || "research_fallback".equalsIgnoreCase(priceQuality.priceSourceType());
    }

    private List<UsConfiguredMethodValuation> capDcfWeight(List<UsConfiguredMethodValuation> methods, double maxWeight) {
        int dcfIndex = indexOfMethod(methods, "dcf");
        if (dcfIndex < 0) {
            return methods;
        }
        UsConfiguredMethodValuation dcf = methods.get(dcfIndex);
        if (dcf.weight() <= maxWeight) {
            return methods;
        }
        double diff = dcf.weight() - maxWeight;
        List<UsConfiguredMethodValuation> adjusted = new ArrayList<>(methods);
        adjusted.set(dcfIndex, copyMethod(
                dcf,
                dcf.bearValue(),
                dcf.baseValue(),
                dcf.bullValue(),
                maxWeight,
                dcf.notes(),
                "weight_capped_low_quality",
                dcf.outlierTrimmed(),
                true
        ));

        List<Integer> preferredTargets = List.of(indexOfMethod(methods, "relative_valuation"), indexOfMethod(methods, "historical_multiple"));
        List<Integer> targetIndexes = preferredTargets.stream().filter(index -> index >= 0).distinct().toList();
        if (targetIndexes.isEmpty()) {
            targetIndexes = methods.stream()
                    .map(UsConfiguredMethodValuation::method)
                    .map(String::toLowerCase)
                    .toList()
                    .stream()
                    .map(name -> indexOfMethod(methods, name))
                    .filter(index -> index >= 0 && index != dcfIndex)
                    .distinct()
                    .toList();
        }
        if (targetIndexes.isEmpty()) {
            return adjusted;
        }
        double perTarget = diff / targetIndexes.size();
        for (Integer targetIndex : targetIndexes) {
            UsConfiguredMethodValuation target = adjusted.get(targetIndex);
            adjusted.set(targetIndex, copyMethod(
                    target,
                    target.bearValue(),
                    target.baseValue(),
                    target.bullValue(),
                    target.weight() + perTarget,
                    target.notes(),
                    target.methodStatus(),
                    target.outlierTrimmed(),
                    true
            ));
        }
        return adjusted;
    }

    private List<UsConfiguredMethodValuation> trimManagedCareDcfOutlier(List<UsConfiguredMethodValuation> methods) {
        int dcfIndex = indexOfMethod(methods, "dcf");
        if (dcfIndex < 0) {
            return methods;
        }
        List<Double> otherBaseValues = methods.stream()
                .filter(method -> !"dcf".equalsIgnoreCase(method.method()))
                .map(UsConfiguredMethodValuation::baseValue)
                .filter(value -> value > 0.0)
                .sorted()
                .toList();
        if (otherBaseValues.size() < 2) {
            return methods;
        }
        double median = medianOfDoubles(otherBaseValues);
        double max = otherBaseValues.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        UsConfiguredMethodValuation dcf = methods.get(dcfIndex);
        if (median <= 0.0 || dcf.baseValue() <= median * 1.75) {
            return methods;
        }
        double clampCeiling = max * 1.25;
        String note = appendNote(dcf.notes(), "Managed care DCF outlier trimmed against peer method median.");
        List<UsConfiguredMethodValuation> adjusted = new ArrayList<>(methods);
        adjusted.set(dcfIndex, copyMethod(
                dcf,
                Math.min(dcf.bearValue(), clampCeiling),
                Math.min(dcf.baseValue(), clampCeiling),
                Math.min(dcf.bullValue(), clampCeiling),
                dcf.weight(),
                note,
                "outlier_trimmed",
                true,
                dcf.weightAdjustedByDataQuality()
        ));
        return adjusted;
    }

    private UsConfiguredMethodValuation copyMethod(
            UsConfiguredMethodValuation method,
            double bearValue,
            double baseValue,
            double bullValue,
            double weight,
            String notes,
            String methodStatus,
            boolean outlierTrimmed,
            boolean weightAdjustedByDataQuality
    ) {
        return new UsConfiguredMethodValuation(
                method.method(),
                MathSupport.round(bearValue),
                MathSupport.round(baseValue),
                MathSupport.round(bullValue),
                MathSupport.round(weight),
                method.rationale(),
                method.inputSnapshotDate(),
                method.primaryMethod(),
                method.assumptionsJson(),
                method.sensitivityJson(),
                notes,
                methodStatus,
                outlierTrimmed,
                weightAdjustedByDataQuality
        );
    }

    private int indexOfMethod(List<UsConfiguredMethodValuation> methods, String methodName) {
        for (int index = 0; index < methods.size(); index += 1) {
            if (methodName.equalsIgnoreCase(methods.get(index).method())) {
                return index;
            }
        }
        return -1;
    }

    private double medianOfDoubles(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        int middle = values.size() / 2;
        if (values.size() % 2 == 0) {
            return (values.get(middle - 1) + values.get(middle)) / 2.0;
        }
        return values.get(middle);
    }

    private String appendNote(String notes, String append) {
        if (notes == null || notes.isBlank()) {
            return append;
        }
        if (notes.contains(append)) {
            return notes;
        }
        return notes + " " + append;
    }

    private String verdict(ValuationResult base, PriceQualityAssessment priceQuality) {
        if ("LOW_CONFIDENCE_STALE_PRICE".equals(priceQuality.valuationStatus())) {
            return "LOW_CONFIDENCE_STALE_PRICE";
        }
        if (!priceQuality.rankable()) {
            return "RESEARCH_ONLY_FALLBACK_PRICE";
        }
        if (base.upside() >= 0.20) {
            return "Undervalued with margin";
        }
        if (base.upside() >= 0.05) {
            return "Slightly undervalued";
        }
        if (base.upside() <= -0.20) {
            return "Overvalued and expectation-heavy";
        }
        if (base.upside() <= -0.05) {
            return "Slightly overvalued";
        }
        return "Fairly valued";
    }

    private String assessment(ValuationResult base, PriceQualityAssessment priceQuality) {
        if (!priceQuality.rankable()) {
            return "research_only";
        }
        if ("LOW_CONFIDENCE_STALE_PRICE".equals(priceQuality.valuationStatus())) {
            return "stale_price";
        }
        if (base.upside() > 0.05) {
            return "discount_to_fair_value";
        }
        if (base.upside() < -0.05) {
            return "premium_to_fair_value";
        }
        return "around_fair_value";
    }

    private String labelByThreshold(double value, double high, double medium) {
        if (value >= high) {
            return "High";
        }
        if (value >= medium) {
            return "Medium";
        }
        return "Low";
    }

    private String classifyCompany(StockSnapshot snapshot) {
        StockFundamentals f = snapshot.fundamentals();
        if (f.revenueGrowth() > 0.20 && f.fcfMargin() < 0.12) {
            return "Hyper-growth SaaS";
        }
        if (f.revenueGrowth() > 0.12 && f.fcfMargin() >= 0.12) {
            return "Profitable Growth";
        }
        if (f.dividendYield() > 0.03 && f.revenueGrowth() < 0.08) {
            return "High Dividend / Income";
        }
        if (f.pe() < 12.0 && f.earningsVolatility() > 0.30) {
            return "Deep Value";
        }
        if (snapshot.industry().toLowerCase(Locale.ROOT).contains("financial")) {
            return "Financial";
        }
        return "Compounder";
    }

    private String sectorTemplate(StockSnapshot snapshot) {
        String industry = snapshot.industry().toLowerCase(Locale.ROOT);
        if (industry.contains("semiconductor")) {
            return "semiconductor_mid_cycle";
        }
        if (industry.contains("technology") || industry.contains("software") || industry.contains("computer") || industry.contains("internet")) {
            return "technology_growth";
        }
        if (industry.contains("financial")) {
            return "financial_ptbv_ddm";
        }
        if (industry.contains("reit")) {
            return "reit_yield_nav";
        }
        return "general_us_equity";
    }

    private String inferExchange(String symbol) {
        if (symbol.startsWith("N")) {
            return "NYSE";
        }
        return "NASDAQ";
    }

    private String inferSector(String industry) {
        String lower = industry.toLowerCase(Locale.ROOT);
        if (lower.contains("technology")
                || lower.contains("semiconductor")
                || lower.contains("software")
                || lower.contains("computer")
                || lower.contains("internet")
                || lower.contains("communication equipment")) {
            return "Technology";
        }
        if (lower.contains("financial") || lower.contains("bank") || lower.contains("insurance")) {
            return "Financials";
        }
        if (lower.contains("consumer") || lower.contains("retail") || lower.contains("restaurant")) {
            return "Consumer";
        }
        if (lower.contains("health") || lower.contains("medical") || lower.contains("pharma") || lower.contains("biotech")) {
            return "Healthcare";
        }
        if (lower.contains("energy") || lower.contains("oil") || lower.contains("gas")) {
            return "Energy";
        }
        return "General";
    }

    private boolean hasRecentCompanyIrGuidance(long securityId) {
        return sourceRegistryRepository.findIdBySourceName(UsSecurityIdentifierService.COMPANY_IR)
                .map(sourceId -> sourceDocumentRepository.existsRecentBySecurityIdAndSourceIdAndDocumentTypes(
                        securityId,
                        sourceId,
                        List.of("GUIDANCE", "EARNINGS_RELEASE", "INVESTOR_DAY", "IR_RELEASE"),
                        LocalDate.now().minusDays(180)
                ))
                .orElse(false);
    }

    private long resolveSecurityId(String rawTicker) {
        return usSecurityMasterService.resolveSecurityId(rawTicker)
                .orElseThrow(() -> new IllegalStateException("Ticker " + rawTicker + " does not exist in security master."));
    }

    private String normalizeTicker(String rawTicker) {
        return rawTicker.trim()
                .toUpperCase(Locale.ROOT)
                .replace(".US", "")
                .replace(".", "-");
    }

    private boolean shouldRefreshStoredSnapshot(String ticker, UsStoredValuationSnapshotRecord stored) {
        if (stored == null) {
            return true;
        }
        if (stored.priceSourceType() == null || stored.priceSourceType().isBlank()) {
            return true;
        }
        if (stored.valuationStatus() == null || stored.valuationStatus().isBlank()) {
            return true;
        }
        if (stored.industryMatchSource() == null || stored.industryMatchSource().isBlank()) {
            return true;
        }
        UsSecurityMaster security = usSecurityMasterService.findByTicker(ticker).orElse(null);
        return requiresManagedCareReclassification(security);
    }

    private boolean requiresManagedCareReclassification(UsSecurityMaster security) {
        if (security == null) {
            return false;
        }
        String normalizedIndustry = normalizeComparable(security.industry());
        boolean managedCareIndustry = normalizedIndustry.contains("managed care")
                || normalizedIndustry.contains("health plan")
                || normalizedIndustry.contains("medical service plan")
                || normalizedIndustry.contains("health benefits")
                || normalizedIndustry.contains("healthcare support services")
                || normalizedIndustry.contains("insurance health")
                || normalizedIndustry.contains("hospital and medical service plans")
                || normalizedIndustry.contains("hospital and medical service plan");
        return managedCareIndustry && !"us_managed_care".equalsIgnoreCase(security.sectorTemplate());
    }

    private String normalizeComparable(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace("&", "and")
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    /** Extracts the primary price source from a compound data-version string (e.g. "longbridge:2026-03-30|stooq:…" → "longbridge:2026-03-30"). */
    private String parsePriceSource(String dataVersion) {
        if (dataVersion == null || dataVersion.isBlank()) {
            return "stooq:fallback";
        }
        int pipe = dataVersion.indexOf('|');
        return pipe >= 0 ? dataVersion.substring(0, pipe) : dataVersion;
    }

    private PriceQualityAssessment assessPriceQuality(StockSnapshot snapshot) {
        String priceSource = parsePriceSource(snapshot.dataVersion());
        String sourceType = resolveSourceType(priceSource);
        LocalDate priceAsOf = extractPriceAsOf(priceSource);
        Integer freshnessDays = priceAsOf == null
                ? null
                : Math.toIntExact(Math.max(0L, ChronoUnit.DAYS.between(priceAsOf, LocalDate.now())));
        boolean isTradable = "tradable".equals(sourceType) || "market_data".equals(sourceType);
        boolean rankable = devRankableOverride || (isTradable && (freshnessDays == null || freshnessDays <= 3));
        String valuationStatus = "ACTIVE";
        String exclusionReason = null;
        if (!isTradable) {
            valuationStatus = "RESEARCH_ONLY_FALLBACK_PRICE";
            exclusionReason = "research_fallback_price";
        }
        if (freshnessDays != null && freshnessDays > 3) {
            valuationStatus = freshnessDays > 10 ? "LOW_CONFIDENCE_STALE_PRICE" : "STALE_PRICE";
            exclusionReason = "stale_price";
        }
        return new PriceQualityAssessment(
                priceAsOf,
                freshnessDays,
                sourceType,
                rankable,
                valuationStatus,
                exclusionReason
        );
    }

    /**
     * Classifies a price source string into one of three tiers:
     * <ul>
     *   <li>{@code tradable} – institutional real-time feed (Longbridge)</li>
     *   <li>{@code market_data} – public near-daily market quote (Stooq); treated as rankable when fresh</li>
     *   <li>{@code research_fallback} – SEC filing date, seed template, or unknown</li>
     * </ul>
     */
    private String resolveSourceType(String priceSource) {
        if (priceSource == null) {
            return "research_fallback";
        }
        if (priceSource.startsWith("longbridge:")) {
            return "tradable";
        }
        if (priceSource.startsWith("stooq:") && !priceSource.equals("stooq:fallback")) {
            return "market_data";
        }
        return "research_fallback";
    }

    private LocalDate extractPriceAsOf(String priceSource) {
        if (priceSource == null || priceSource.isBlank() || !priceSource.contains(":")) {
            return null;
        }
        String candidate = priceSource.substring(priceSource.indexOf(':') + 1).trim();
        try {
            return LocalDate.parse(candidate);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Long coerceLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private void captureStringMapEntry(
            Map<String, Object> assumptions,
            String assumptionKey,
            Map<String, String> target,
            String targetKey
    ) {
        Object raw = assumptions.get(assumptionKey);
        if (raw != null) {
            String value = raw.toString();
            if (!value.isBlank()) {
                target.put(targetKey, value);
            }
        }
    }

    private List<String> parseStringList(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<List<String>>() { });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private double safe(java.math.BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private double estimateDilutedShares(StockSnapshot snapshot) {
        int hash = Math.abs(snapshot.symbol().hashCode());
        double baseline = 200_000_000 + (hash % 1200) * 1_000_000.0;
        double coverageTilt = 1.0 + snapshot.fundamentals().analystCoverage() * 0.4;
        return baseline * coverageTilt;
    }

    private double resolveDilutedShares(StockSnapshot snapshot, UsSecClient.UsSecProfile profile) {
        if (profile != null && profile.sharesOutstanding() != null && profile.sharesOutstanding() > 0) {
            return profile.sharesOutstanding();
        }
        return estimateDilutedShares(snapshot);
    }

    private double estimateOwnerEarnings(
            StockSnapshot snapshot,
            UsSecClient.UsSecProfile profile,
            double dilutedShares
    ) {
        if (profile != null && profile.annualOperatingCashFlow() != null && profile.annualCapex() != null) {
            return profile.annualOperatingCashFlow() - profile.annualCapex();
        }
        return snapshot.price() * dilutedShares * Math.max(snapshot.fundamentals().fcfMargin(), 0.03);
    }

    private StockSnapshot usSnapshot(String ticker) {
        return marketDataService.getSnapshot(Market.US, ticker);
    }

    private UsSecClient.UsSecProfile usProfile(String ticker) {
        return usSecClient.fetchProfile(ticker).orElse(null);
    }

    private double usDailyChange(String ticker) {
        OptionalDouble longbridgeChange = usLongbridgeClient.fetchMarketData(ticker)
                .map(UsLongbridgeClient.UsLongbridgeMarketData::changeRate)
                .filter(Double::isFinite)
                .map(value -> Math.abs(value) > 1.0 ? value / 100.0 : value)
                .stream()
                .mapToDouble(Double::doubleValue)
                .findFirst();
        if (longbridgeChange.isPresent()) {
            return longbridgeChange.getAsDouble();
        }
        return usStooqClient.fetchQuote(ticker)
                .map(UsStooqClient.UsQuote::dailyChange)
                .orElse(0.0);
    }

    private UsValuationRunRequest normalize(UsValuationRunRequest request) {
        if (request == null) {
            return UsValuationRunRequest.defaults();
        }
        String style = request.style() == null || request.style().isBlank() ? "balanced" : request.style();
        String horizon = request.horizon() == null || request.horizon().isBlank() ? "6-18m" : request.horizon();
        List<String> forceMethods = request.forceMethods() == null ? List.of() : request.forceMethods();
        Map<String, Double> assumptions = request.customAssumptions() == null ? Map.of() : request.customAssumptions();
        return new UsValuationRunRequest(style, horizon, request.useConsensus(), forceMethods, assumptions);
    }

    private record ComputedValuation(
            UsValuationRunResponse run,
            UsValuationReportResponse report
    ) {
    }

    private record PriceQualityAssessment(
            LocalDate priceAsOf,
            Integer priceFreshnessDays,
            String priceSourceType,
            boolean rankable,
            String valuationStatus,
            String exclusionReason
    ) {
    }
}
