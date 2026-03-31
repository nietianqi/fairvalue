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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class UsEquityValuationService {
    private final MarketDataService marketDataService;
    private final ValuationService valuationService;
    private final UsSecClient usSecClient;
    private final UsStooqClient usStooqClient;
    private final UsSecurityMasterService usSecurityMasterService;
    private final UsSecurityClassificationService usSecurityClassificationService;
    private final UsDataQualityAuditService usDataQualityAuditService;
    private final UsConfiguredValuationModelsService usConfiguredValuationModelsService;
    private final UsValuationPersistenceService usValuationPersistenceService;
    private final FinancialDerivedMetricsRepository financialDerivedMetricsRepository;
    private final FinancialQualityScoresRepository financialQualityScoresRepository;
    private final SourceRegistryRepository sourceRegistryRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final ObjectMapper objectMapper;

    public UsEquityValuationService(
            MarketDataService marketDataService,
            ValuationService valuationService,
            UsSecClient usSecClient,
            UsStooqClient usStooqClient,
            UsSecurityMasterService usSecurityMasterService,
            UsSecurityClassificationService usSecurityClassificationService,
            UsDataQualityAuditService usDataQualityAuditService,
            UsConfiguredValuationModelsService usConfiguredValuationModelsService,
            UsValuationPersistenceService usValuationPersistenceService,
            FinancialDerivedMetricsRepository financialDerivedMetricsRepository,
            FinancialQualityScoresRepository financialQualityScoresRepository,
            SourceRegistryRepository sourceRegistryRepository,
            SourceDocumentRepository sourceDocumentRepository,
            ObjectMapper objectMapper
    ) {
        this.marketDataService = marketDataService;
        this.valuationService = valuationService;
        this.usSecClient = usSecClient;
        this.usStooqClient = usStooqClient;
        this.usSecurityMasterService = usSecurityMasterService;
        this.usSecurityClassificationService = usSecurityClassificationService;
        this.usDataQualityAuditService = usDataQualityAuditService;
        this.usConfiguredValuationModelsService = usConfiguredValuationModelsService;
        this.usValuationPersistenceService = usValuationPersistenceService;
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
        UsValuationRunRequest effective = normalize(request);
        StockSnapshot snapshot = usSnapshot(ticker);
        UsSecClient.UsSecProfile profile = usProfile(ticker);
        UsSecurityMaster classified = usSecurityClassificationService.ensureClassification(ticker, snapshot, profile);
        ValuationResult base = valuationService.valuate(Market.US, ticker);
        UsConfiguredValuationResult configured = usConfiguredValuationModelsService.evaluate(snapshot, classified, effective.customAssumptions());

        List<UsConfiguredMethodValuation> selectedMethods = applyMethodSelection(configured.methods(), effective.forceMethods());
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
        List<UsScenarioOutput> scenarioMatrix = scenarioMatrix(base, range, effectiveConfidence, riskMatrixResult);

        double rawMarginOfSafety = (range.mid() - base.price()) / Math.max(range.mid(), 0.1);
        double marginOfSafety = MathSupport.round(rawMarginOfSafety - configured.requiredMarginOfSafety());
        String impliedExpectation = configured.reverseDcfAnalysis().impliedExpectationLabel();

        Map<String, String> explanationBlocks = new LinkedHashMap<>();
        explanationBlocks.put("one_line_verdict", verdict(base));
        explanationBlocks.put("executive_summary", "Multi-method US valuation combines configurable FCFF, reverse DCF, relative multiples, and historical anchors.");
        explanationBlocks.put("model_selection", configured.modelSelectionReason());
        explanationBlocks.put("reverse_dcf", "Reverse DCF implied expectation=" + impliedExpectation + ", notes=" + configured.reverseDcfAnalysis().notesJson());
        explanationBlocks.put("risk_commentary", "Risk matrix adjusted WACC by "
                + MathSupport.round(riskMatrixResult.waccAdjustment())
                + ", required margin of safety by "
                + MathSupport.round(riskMatrixResult.marginOfSafetyAdjustment())
                + ", and shifted bear/bull probabilities by "
                + MathSupport.round(riskMatrixResult.bearProbabilityDelta())
                + "/"
                + MathSupport.round(riskMatrixResult.bullProbabilityDelta())
                + ".");
        explanationBlocks.put("margin_of_safety", "Raw margin="
                + MathSupport.round(rawMarginOfSafety)
                + ", required margin="
                + MathSupport.round(configured.requiredMarginOfSafety())
                + ", effective margin="
                + marginOfSafety);
        explanationBlocks.put("style_horizon", "Style=" + effective.style() + ", Horizon=" + effective.horizon() + ", Consensus=" + effective.useConsensus());

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
                verdict(base),
                classified
        );

        return new UsValuationRunResponse(
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
                explanationBlocks
        );
    }

    public UsValuationSummaryResponse summary(String ticker) {
        UsValuationRunResponse run = runValuation(ticker, new UsValuationRunRequest("balanced", "6-18m", true, List.of(), Map.of()));
        ValuationResult base = valuationService.valuate(Market.US, ticker);

        UsFairValueRange buyZone = new UsFairValueRange(
                MathSupport.round(run.fairValueRange().low() * 0.85),
                MathSupport.round(run.fairValueRange().low()),
                MathSupport.round(run.fairValueRange().mid() * 0.92)
        );
        UsFairValueRange holdZone = new UsFairValueRange(
                MathSupport.round(run.fairValueRange().low()),
                MathSupport.round(run.fairValueRange().mid()),
                MathSupport.round(run.fairValueRange().high())
        );
        UsFairValueRange avoidZone = new UsFairValueRange(
                MathSupport.round(run.fairValueRange().high()),
                MathSupport.round(run.fairValueRange().high() * 1.08),
                MathSupport.round(run.fairValueRange().high() * 1.18)
        );

        return new UsValuationSummaryResponse(
                run.ticker(),
                verdict(base),
                run.fairValueRange(),
                assessment(base),
                base.upside(),
                base.confidence(),
                buyZone,
                holdZone,
                avoidZone
        );
    }

    public UsValuationReportResponse report(String ticker) {
        UsValuationRunResponse run = runValuation(ticker, new UsValuationRunRequest("balanced", "6-18m", true, List.of(), Map.of()));
        UsDataQualityResponse dataQuality = dataQuality(ticker);
        UsFinancialQualityResponse quality = financialQuality(ticker);
        UsEquityProfileResponse profile = profile(ticker);

        Map<String, Object> dataQualityAudit = new LinkedHashMap<>();
        dataQualityAudit.put("latest_10k_date", dataQuality.latest10kDate());
        dataQualityAudit.put("latest_10q_date", dataQuality.latest10qDate());
        dataQualityAudit.put("share_count_verified", dataQuality.shareCountVerified());
        dataQualityAudit.put("sbc_quantified", dataQuality.sbcQuantified());
        dataQualityAudit.put("confidence_level", dataQuality.confidenceLevel());
        dataQualityAudit.put("guidance_status", dataQuality.guidanceStatus());
        dataQualityAudit.put("missing_items", dataQuality.missingItems());
        dataQualityAudit.put("warning_flags", dataQuality.warningFlags());

        Map<String, Object> businessAndMoat = new LinkedHashMap<>();
        double moatScore = MathSupport.round(MathSupport.clamp(0.45 + quality.revenueQualityScore() * 0.30 + quality.capitalEfficiencyScore() * 0.25, 0.20, 0.95));
        businessAndMoat.put("company_type", profile.companyType());
        businessAndMoat.put("sector_template", profile.sectorTemplate());
        businessAndMoat.put("moat_score", moatScore);
        businessAndMoat.put("moat_level", moatScore > 0.75 ? "Wide" : moatScore > 0.58 ? "Narrow" : "None");

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

        ValuationResult base = valuationService.valuate(Market.US, ticker);
        Map<String, Object> usMarketModifiers = new LinkedHashMap<>();
        usMarketModifiers.put("sbc_ratio", MathSupport.round(usSnapshot(ticker).fundamentals().sbcRatio()));
        usMarketModifiers.put("buyback_yield", MathSupport.round(usSnapshot(ticker).fundamentals().buybackYield()));
        usMarketModifiers.put("analyst_coverage", MathSupport.round(usSnapshot(ticker).fundamentals().analystCoverage()));
        usMarketModifiers.put("implied_expectation", run.impliedExpectation());
        usMarketModifiers.put("data_version", usSnapshot(ticker).dataVersion());

        Map<String, String> actionable = new LinkedHashMap<>();
        actionable.put("buy", "Consider adding only inside buy-zone and when risk matrix has no new High/High item.");
        actionable.put("hold", "Hold when price stays in hold-zone and thesis quality metrics remain stable.");
        actionable.put("reduce", "Reduce when price enters avoid-zone or expectation label stays aggressive without catalyst support.");

        return new UsValuationReportResponse(
                ticker.toUpperCase(Locale.ROOT),
                verdict(base),
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
                "Model risk comes from assumptions on WACC, growth durability, multiple regime and diluted share trajectory."
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
                method.rationale()
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
                            method.notes()
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
                        method.notes()
                ))
                .toList();
    }

    private String verdict(ValuationResult base) {
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

    private String assessment(ValuationResult base) {
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
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
        return usStooqClient.fetchQuote(ticker)
                .map(UsStooqClient.UsQuote::dailyChange)
                .orElse(0.0);
    }

    private UsValuationRunRequest normalize(UsValuationRunRequest request) {
        if (request == null) {
            return new UsValuationRunRequest("balanced", "6-18m", true, List.of(), Map.of());
        }
        String style = request.style() == null || request.style().isBlank() ? "balanced" : request.style();
        String horizon = request.horizon() == null || request.horizon().isBlank() ? "6-18m" : request.horizon();
        List<String> forceMethods = request.forceMethods() == null ? List.of() : request.forceMethods();
        Map<String, Double> assumptions = request.customAssumptions() == null ? Map.of() : request.customAssumptions();
        return new UsValuationRunRequest(style, horizon, request.useConsensus(), forceMethods, assumptions);
    }
}
