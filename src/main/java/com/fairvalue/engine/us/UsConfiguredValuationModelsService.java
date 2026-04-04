package com.fairvalue.engine.us;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairvalue.engine.domain.StockFundamentals;
import com.fairvalue.engine.domain.StockSnapshot;
import com.fairvalue.engine.repository.DataQualityAuditRepository;
import com.fairvalue.engine.repository.FinancialDerivedMetricsRepository;
import com.fairvalue.engine.repository.FinancialQualityScoresRepository;
import com.fairvalue.engine.repository.FinancialStandardizedRepository;
import com.fairvalue.engine.repository.MarketPriceDailyRepository;
import com.fairvalue.engine.repository.MarketSnapshotRepository;
import com.fairvalue.engine.valuation.MarketAdjustment;
import com.fairvalue.engine.valuation.strategy.MathSupport;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class UsConfiguredValuationModelsService {
    private static final String RELATIVE_PEER_SOURCE =
            "security_master+market_snapshot+financial_standardized+financial_derived_metrics";
    private static final int RELATIVE_PEER_LIMIT = 8;
    private static final int RELATIVE_PEER_MIN_REQUIRED = 3;
    private final UsSecurityMasterService usSecurityMasterService;
    private final UsPeerUniverseRulesService usPeerUniverseRulesService;
    private final UsValuationConfigService usValuationConfigService;
    private final FinancialStandardizedRepository financialStandardizedRepository;
    private final FinancialDerivedMetricsRepository financialDerivedMetricsRepository;
    private final FinancialQualityScoresRepository financialQualityScoresRepository;
    private final DataQualityAuditRepository dataQualityAuditRepository;
    private final MarketPriceDailyRepository marketPriceDailyRepository;
    private final MarketSnapshotRepository marketSnapshotRepository;
    private final UsSecClient usSecClient;
    private final UsExternalValuationParameterService usExternalValuationParameterService;
    private final UsRiskMatrixService usRiskMatrixService;
    private final ObjectMapper objectMapper;

    public UsConfiguredValuationModelsService(
            UsSecurityMasterService usSecurityMasterService,
            UsPeerUniverseRulesService usPeerUniverseRulesService,
            UsValuationConfigService usValuationConfigService,
            FinancialStandardizedRepository financialStandardizedRepository,
            FinancialDerivedMetricsRepository financialDerivedMetricsRepository,
            FinancialQualityScoresRepository financialQualityScoresRepository,
            DataQualityAuditRepository dataQualityAuditRepository,
            MarketPriceDailyRepository marketPriceDailyRepository,
            MarketSnapshotRepository marketSnapshotRepository,
            UsSecClient usSecClient,
            UsExternalValuationParameterService usExternalValuationParameterService,
            UsRiskMatrixService usRiskMatrixService,
            ObjectMapper objectMapper
    ) {
        this.usSecurityMasterService = usSecurityMasterService;
        this.usPeerUniverseRulesService = usPeerUniverseRulesService;
        this.usValuationConfigService = usValuationConfigService;
        this.financialStandardizedRepository = financialStandardizedRepository;
        this.financialDerivedMetricsRepository = financialDerivedMetricsRepository;
        this.financialQualityScoresRepository = financialQualityScoresRepository;
        this.dataQualityAuditRepository = dataQualityAuditRepository;
        this.marketPriceDailyRepository = marketPriceDailyRepository;
        this.marketSnapshotRepository = marketSnapshotRepository;
        this.usSecClient = usSecClient;
        this.usExternalValuationParameterService = usExternalValuationParameterService;
        this.usRiskMatrixService = usRiskMatrixService;
        this.objectMapper = objectMapper;
    }

    public UsValuationModelContext loadContext(StockSnapshot snapshot, UsSecurityMaster security) {
        Long securityId = security != null && security.id() != null
                ? security.id()
                : usSecurityMasterService.resolveSecurityId(snapshot.symbol()).orElse(null);

        List<UsFinancialStandardizedRecord> financials = securityId == null
                ? List.of()
                : financialStandardizedRepository.findBySecurityId(securityId);
        List<UsFinancialDerivedMetricRecord> derivedMetrics = securityId == null
                ? List.of()
                : financialDerivedMetricsRepository.findBySecurityId(securityId);
        UsDataQualityAuditRecord latestAudit = securityId == null
                ? null
                : dataQualityAuditRepository.findLatestBySecurityId(securityId).orElse(null);
        List<UsMarketPriceDailyRecord> dailyHistory = securityId == null
                ? List.of()
                : marketPriceDailyRepository.findLatestBySecurityId(securityId, 252);
        List<UsMarketSnapshotRecord> marketSnapshots = securityId == null
                ? List.of()
                : marketSnapshotRepository.findLatestBySecurityId(securityId, 252);

        UsFinancialStandardizedRecord latestTtm = firstByPeriodType(financials, "TTM");
        UsFinancialStandardizedRecord latestAnnual = firstByPeriodType(financials, "FY");
        UsFinancialStandardizedRecord latestQuarter = firstByPeriodType(financials, "Q");
        UsFinancialDerivedMetricRecord latestDerived = derivedMetrics.stream().findFirst().orElse(null);
        UsFinancialQualityScoreRecord latestQualityScore = securityId == null
                ? null
                : financialQualityScoresRepository.findLatestAvailableBySecurityId(securityId).orElse(null);
        UsSecClient.UsSecProfile secProfile = usSecClient.fetchProfile(snapshot.symbol()).orElse(null);
        UsResolvedValuationConfig baseConfig = usValuationConfigService.resolve(
                securityId,
                security == null ? null : security.sectorTemplate(),
                LocalDate.now()
        );
        UsExternalValuationParameterService.ExternalParameterSnapshot externalParameterSnapshot =
                usExternalValuationParameterService.resolve(snapshot, security);
        Map<String, Double> numericParameters = new LinkedHashMap<>(baseConfig.numericParameters());
        numericParameters.putAll(externalParameterSnapshot.parameters());
        Map<String, String> parameterSources = new LinkedHashMap<>(baseConfig.parameterSources());
        parameterSources.putAll(externalParameterSnapshot.sources());
        UsResolvedValuationConfig config = new UsResolvedValuationConfig(
                baseConfig.sectorTemplate(),
                baseConfig.primaryMethods(),
                baseConfig.normalizedWeights(),
                Map.copyOf(numericParameters),
                baseConfig.defaultMarginOfSafety(),
                baseConfig.riskNotes(),
                Map.copyOf(parameterSources)
        );

        return new UsValuationModelContext(
                securityId == null ? -1L : securityId,
                snapshot,
                security,
                secProfile,
                config,
                financials,
                derivedMetrics,
                latestTtm,
                latestAnnual,
                latestQuarter,
                latestDerived,
                latestQualityScore,
                latestAudit,
                dailyHistory,
                marketSnapshots,
                externalParameterSnapshot
        );
    }

    public RelativePeerSelectionView selectRelativePeersReadOnly(UsValuationModelContext context, int limit) {
        RelativePeerSelection selection = selectRelativePeers(context, limit);
        List<UsRelativePeerComparable> peers = selection.peers();
        return new RelativePeerSelectionView(
                peers,
                selection.candidateCount(),
                peerSelectionBasis(context, peers),
                peerSelectionBreakdown(context, peers),
                selection.filterSummary(),
                selection.ruleVersion(),
                selection.filterMetrics(),
                selection.selectionMode(),
                peers.isEmpty() ? "template_only" : "peer_set_plus_template",
                peers.isEmpty() ? "template_only" : RELATIVE_PEER_SOURCE
        );
    }

    public UsConfiguredValuationResult evaluate(
            StockSnapshot snapshot,
            UsSecurityMaster security,
            Map<String, Double> customAssumptions
    ) {
        UsValuationModelContext context = loadContext(snapshot, security);
        StockFundamentals fundamentals = snapshot.fundamentals();
        UsResolvedValuationConfig config = context.config();

        double baselineWacc = resolveWacc(context, customAssumptions);
        UsRiskMatrixResult riskMatrixResult = usRiskMatrixService.evaluate(context, baselineWacc);
        double effectiveWacc = clampWacc(context, baselineWacc + riskMatrixResult.waccAdjustment());
        double terminalGrowth = resolveTerminalGrowth(context, customAssumptions, effectiveWacc);
        double targetFcfMargin = resolveTargetFcfMargin(context);
        double targetEvEbitda = parameter(context, "relative.target_ev_ebitda", defaultRelativeEvEbitda(fundamentals));
        double targetPe = parameter(context, "relative.target_pe", defaultRelativePe(fundamentals));
        int forecastYears = (int) Math.round(parameter(context, "dcf.forecast_years", 5.0));
        double initialGrowthCap = parameter(context, "dcf.initial_growth_cap", Math.max(fundamentals.revenueGrowth(), 0.12));
        double growthFloor = parameter(context, "dcf.growth_floor", Math.max(terminalGrowth, 0.03));
        double impliedGrowthFloor = parameter(context, "reverse_dcf.implied_growth_floor", -0.05);
        double impliedGrowthCeiling = parameter(context, "reverse_dcf.implied_growth_ceiling", 0.30);

        DcfScenarioInputs baseDcfInputs = resolveDcfInputs(
                context,
                effectiveWacc,
                terminalGrowth,
                forecastYears,
                initialGrowthCap,
                growthFloor,
                targetFcfMargin
        );
        DcfScenarioInputs bearDcfInputs = baseDcfInputs.withScenario(
                MathSupport.clamp(baseDcfInputs.wacc + 0.010, baseDcfInputs.terminalGrowth + 0.015, 0.18),
                MathSupport.clamp(baseDcfInputs.terminalGrowth - 0.005, 0.010, 0.040),
                MathSupport.clamp(baseDcfInputs.initialGrowth - 0.035, -0.05, 0.30),
                MathSupport.clamp(baseDcfInputs.growthFloor - 0.010, -0.02, 0.12),
                MathSupport.clamp(baseDcfInputs.targetFcfMargin - 0.020, 0.04, 0.45)
        );
        DcfScenarioInputs bullDcfInputs = baseDcfInputs.withScenario(
                MathSupport.clamp(baseDcfInputs.wacc - 0.010, baseDcfInputs.terminalGrowth + 0.015, 0.16),
                MathSupport.clamp(baseDcfInputs.terminalGrowth + 0.005, 0.015, 0.045),
                MathSupport.clamp(baseDcfInputs.initialGrowth + 0.035, -0.05, 0.45),
                MathSupport.clamp(baseDcfInputs.growthFloor + 0.010, -0.01, 0.16),
                MathSupport.clamp(baseDcfInputs.targetFcfMargin + 0.020, 0.05, 0.55)
        );

        ValuationScenario dcfScenario = dcfValuation(context, baseDcfInputs, bearDcfInputs, bullDcfInputs);
        ValuationScenario relativeScenario = relativeValuation(context, targetEvEbitda, targetPe);
        ValuationScenario historicalScenario = historicalMultipleValuation(context);
        ReverseScenario reverseScenario = reverseDcfValuation(context, baseDcfInputs, impliedGrowthFloor, impliedGrowthCeiling);

        List<UsConfiguredMethodValuation> methods = buildMethods(config, dcfScenario, historicalScenario, relativeScenario, reverseScenario);
        List<MarketAdjustment> adjustments = buildAdjustments(context, effectiveWacc, terminalGrowth, riskMatrixResult);
        List<String> riskFlags = buildRiskFlags(context, reverseScenario.analysis.impliedRevenueCagr(), riskMatrixResult);
        Map<String, Double> drivers = buildDrivers(context, effectiveWacc, terminalGrowth, targetFcfMargin, reverseScenario.analysis, riskMatrixResult);
        double confidenceBase = confidenceBase(context, riskMatrixResult);
        double requiredMarginOfSafety = MathSupport.round(MathSupport.clamp(
                config.defaultMarginOfSafety() + riskMatrixResult.marginOfSafetyAdjustment(),
                0.12,
                0.45
        ));
        String methodology = "US PRD valuation uses sector-template-configured DCF/FCFF, historical multiple, relative valuation, and always-on reverse DCF.";
        String modelSelectionReason = "Methods selected from sector_template_config=" + config.sectorTemplate()
                + " with weights=" + config.normalizedWeights().keySet()
                + " and parameter overrides from valuation_parameter_set.";

        return new UsConfiguredValuationResult(
                context,
                methods,
                reverseScenario.analysis,
                riskMatrixResult,
                drivers,
                adjustments,
                riskFlags,
                confidenceBase,
                requiredMarginOfSafety,
                methodology,
                modelSelectionReason
        );
    }

    private List<UsConfiguredMethodValuation> buildMethods(
            UsResolvedValuationConfig config,
            ValuationScenario dcfScenario,
            ValuationScenario historicalScenario,
            ValuationScenario relativeScenario,
            ReverseScenario reverseScenario
    ) {
        Map<String, ValuationScenario> scenarios = Map.of(
                "dcf", dcfScenario,
                "historical_multiple", historicalScenario,
                "relative_valuation", relativeScenario,
                "reverse_dcf", reverseScenario.methodScenario
        );

        List<UsConfiguredMethodValuation> methods = new ArrayList<>();
        for (String method : config.primaryMethods()) {
            ValuationScenario scenario = scenarios.get(method);
            if (scenario == null) {
                continue;
            }
            double weight = config.normalizedWeights().getOrDefault(method, 0.0);
            methods.add(new UsConfiguredMethodValuation(
                    method,
                    MathSupport.round(scenario.bearValue),
                    MathSupport.round(scenario.baseValue),
                    MathSupport.round(scenario.bullValue),
                    MathSupport.round(weight),
                    scenario.rationale,
                    scenario.inputSnapshotDate,
                    true,
                    scenario.assumptionsJson,
                    scenario.sensitivityJson,
                    scenario.notes
            ));
        }
        if (methods.stream().noneMatch(method -> "reverse_dcf".equals(method.method()))) {
            methods.add(new UsConfiguredMethodValuation(
                    "reverse_dcf",
                    MathSupport.round(reverseScenario.methodScenario.bearValue),
                    MathSupport.round(reverseScenario.methodScenario.baseValue),
                    MathSupport.round(reverseScenario.methodScenario.bullValue),
                    MathSupport.round(config.normalizedWeights().getOrDefault("reverse_dcf", 0.20)),
                    reverseScenario.methodScenario.rationale,
                    reverseScenario.methodScenario.inputSnapshotDate,
                    true,
                    reverseScenario.methodScenario.assumptionsJson,
                    reverseScenario.methodScenario.sensitivityJson,
                    reverseScenario.methodScenario.notes
            ));
        }
        return methods.stream()
                .sorted(Comparator.comparing(UsConfiguredMethodValuation::method))
                .toList();
    }

    private DcfScenarioInputs resolveDcfInputs(
            UsValuationModelContext context,
            double wacc,
            double terminalGrowth,
            int forecastYears,
            double initialGrowthCap,
            double growthFloor,
            double targetFcfMargin
    ) {
        StockSnapshot snapshot = context.snapshot();
        double revenue = positive(
                latestPositiveFinancialValue(context, UsFinancialStandardizedRecord::revenue, "TTM", "FY", "Q"),
                secAnnualRevenue(context),
                0.0
        );
        double shares = positive(
                latestPositiveFinancialValue(context, UsFinancialStandardizedRecord::dilutedShares, "TTM", "FY", "Q"),
                latestPositiveFinancialValue(context, UsFinancialStandardizedRecord::basicShares, "TTM", "FY", "Q"),
                secSharesOutstanding(context),
                impliedSharesFromMarketSnapshot(context),
                0.0
        );
        double netDebt = resolveNetDebt(context);
        double initialGrowth = MathSupport.clamp(
                Math.min(snapshot.fundamentals().revenueGrowth(), initialGrowthCap),
                -0.05,
                initialGrowthCap
        );

        return new DcfScenarioInputs(
                revenue,
                shares,
                netDebt,
                wacc,
                terminalGrowth,
                forecastYears,
                initialGrowth,
                growthFloor,
                targetFcfMargin,
                resolveCurrentFcfMargin(context)
        );
    }

    private ValuationScenario dcfValuation(
            UsValuationModelContext context,
            DcfScenarioInputs baseInputs,
            DcfScenarioInputs bearInputs,
            DcfScenarioInputs bullInputs
    ) {
        StockSnapshot snapshot = context.snapshot();
        boolean structured = baseInputs.revenue > 0.0 && baseInputs.shares > 0.0;
        double bearValue;
        double baseValue;
        double bullValue;
        String notes;

        if (structured) {
            bearValue = equityPerShareFromFcff(bearInputs);
            baseValue = equityPerShareFromFcff(baseInputs);
            bullValue = equityPerShareFromFcff(bullInputs);
            notes = "Configured FCFF model uses financial_standardized + financial_derived_metrics with explicit WACC, terminal growth, and margin fade.";
        } else {
            double factor = MathSupport.clamp(
                    1.0
                            + context.snapshot().fundamentals().revenueGrowth() * 1.40
                            + baseInputs.currentFcfMargin * 1.20
                            + Math.max(baseInputs.targetFcfMargin - baseInputs.currentFcfMargin, 0.0) * 0.55
                            - baseInputs.wacc
                            + baseInputs.terminalGrowth * 1.60
                            - context.snapshot().fundamentals().sbcRatio() * 0.45,
                    0.72,
                    1.55
            );
            baseValue = snapshot.price() * factor;
            bearValue = baseValue * 0.86;
            bullValue = baseValue * 1.14;
            notes = "Structured FCFF inputs are incomplete; DCF fell back to snapshot-driven configurable anchor while keeping template parameters active.";
        }

        Map<String, Object> assumptions = new LinkedHashMap<>();
        assumptions.put("source_tables", structured
                ? List.of("financial_standardized", "financial_derived_metrics", "data_quality_audit")
                : List.of("stock_snapshot"));
        assumptions.put("forecast_years", baseInputs.forecastYears);
        assumptions.put("initial_growth", MathSupport.round(baseInputs.initialGrowth));
        assumptions.put("growth_floor", MathSupport.round(baseInputs.growthFloor));
        assumptions.put("target_fcf_margin", MathSupport.round(baseInputs.targetFcfMargin));
        assumptions.put("effective_wacc", MathSupport.round(baseInputs.wacc));
        assumptions.put("terminal_growth", MathSupport.round(baseInputs.terminalGrowth));
        assumptions.put("structured_inputs", structured);
        assumptions.put("parameter_sources", parameterSources(context,
                Map.entry("wacc_base", "wacc.base"),
                Map.entry("wacc_rf", "wacc.rf"),
                Map.entry("wacc_erp", "wacc.erp"),
                Map.entry("wacc_beta", "wacc.beta"),
                Map.entry("terminal_growth", "terminal_growth.base")
        ));

        Map<String, Object> sensitivity = new LinkedHashMap<>();
        sensitivity.put("bear_wacc", MathSupport.round(bearInputs.wacc));
        sensitivity.put("base_wacc", MathSupport.round(baseInputs.wacc));
        sensitivity.put("bull_wacc", MathSupport.round(bullInputs.wacc));
        sensitivity.put("bear_target_fcf_margin", MathSupport.round(bearInputs.targetFcfMargin));
        sensitivity.put("base_target_fcf_margin", MathSupport.round(baseInputs.targetFcfMargin));
        sensitivity.put("bull_target_fcf_margin", MathSupport.round(bullInputs.targetFcfMargin));

        return new ValuationScenario(
                bearValue,
                baseValue,
                bullValue,
                "DCF/FCFF discounts multi-year cash flows using template-configured WACC, terminal growth, and margin convergence.",
                LocalDate.now(),
                toJson(assumptions),
                toJson(sensitivity),
                notes
        );
    }

    private ValuationScenario relativeValuation(
            UsValuationModelContext context,
            double targetEvEbitda,
            double targetPe
    ) {
        StockSnapshot snapshot = context.snapshot();
        StockFundamentals fundamentals = snapshot.fundamentals();
        RelativePeerSelectionView peerSelection = selectRelativePeersReadOnly(context, RELATIVE_PEER_LIMIT);
        List<UsRelativePeerComparable> peers = peerSelection.peers();
        String peerSelectionBasis = peerSelection.selectionBasis();
        Map<String, Long> peerSelectionBreakdown = peerSelection.selectionBreakdown();
        List<String> peerTickers = peers.stream()
                .map(UsRelativePeerComparable::ticker)
                .toList();
        Double peerMedianEvEbitda = median(peers.stream()
                .map(this::peerEvEbitda)
                .filter(value -> value != null && value > 0.0)
                .toList());
        Double peerMedianPe = median(peers.stream()
                .map(UsRelativePeerComparable::peTtm)
                .filter(value -> value != null && value.doubleValue() > 0.0)
                .map(BigDecimal::doubleValue)
                .toList());
        Double peerMedianPb = median(peers.stream()
                .map(UsRelativePeerComparable::pb)
                .filter(value -> value != null && value.doubleValue() > 0.0)
                .map(BigDecimal::doubleValue)
                .toList());
        double effectiveTargetEvEbitda = peerMedianEvEbitda == null ? targetEvEbitda : peerMedianEvEbitda;
        double effectiveTargetPe = peerMedianPe == null ? targetPe : peerMedianPe;

        List<Double> anchors = new ArrayList<>();
        Map<String, Double> components = new LinkedHashMap<>();

        if (fundamentals.evEbitda() > 0.0 && effectiveTargetEvEbitda > 0.0) {
            double factor = MathSupport.clamp(effectiveTargetEvEbitda / fundamentals.evEbitda(), 0.70, 1.40);
            anchors.add(factor);
            components.put("ev_ebitda_factor", MathSupport.round(factor));
        }
        if (fundamentals.pe() > 0.0 && effectiveTargetPe > 0.0) {
            double factor = MathSupport.clamp(effectiveTargetPe / fundamentals.pe(), 0.70, 1.35);
            anchors.add(factor);
            components.put("pe_factor", MathSupport.round(factor));
        }
        if (fundamentals.pb() > 0.0 && peerMedianPb != null && peerMedianPb > 0.0) {
            double factor = MathSupport.clamp(peerMedianPb / fundamentals.pb(), 0.75, 1.30);
            anchors.add(factor);
            components.put("pb_factor", MathSupport.round(factor));
        }

        double anchor = anchors.isEmpty()
                ? 1.0
                : anchors.stream().mapToDouble(Double::doubleValue).average().orElse(1.0);
        double baseValue = snapshot.price() * anchor;
        double band = anchors.size() >= 3 ? 0.08 : anchors.size() >= 2 ? 0.10 : peers.isEmpty() ? 0.14 : 0.12;

        Map<String, Object> assumptions = new LinkedHashMap<>();
        assumptions.put("source_tables", List.of(
                "security_master",
                "market_snapshot",
                "financial_standardized",
                "financial_derived_metrics"
        ));
        assumptions.put("configured_target_ev_ebitda", MathSupport.round(targetEvEbitda));
        assumptions.put("configured_target_pe", MathSupport.round(targetPe));
        assumptions.put("effective_target_ev_ebitda", MathSupport.round(effectiveTargetEvEbitda));
        assumptions.put("effective_target_pe", MathSupport.round(effectiveTargetPe));
        assumptions.put("peer_target_ev_ebitda", peerMedianEvEbitda == null ? null : MathSupport.round(peerMedianEvEbitda));
        assumptions.put("peer_target_pe", peerMedianPe == null ? null : MathSupport.round(peerMedianPe));
        assumptions.put("peer_target_pb", peerMedianPb == null ? null : MathSupport.round(peerMedianPb));
        assumptions.put("effective_target_ev_ebitda_source", peerMedianEvEbitda == null ? "configured_template" : "peer_set");
        assumptions.put("effective_target_pe_source", peerMedianPe == null ? "configured_template" : "peer_set");
        assumptions.put("component_count", components.size());
        assumptions.put("components", components);
        assumptions.put("peer_candidate_count", peerSelection.candidateCount());
        assumptions.put("peer_set_size", peers.size());
        assumptions.put("peer_set_tickers", peerTickers);
        assumptions.put("peer_selection_basis", peerSelectionBasis);
        assumptions.put("peer_selection_breakdown", peerSelectionBreakdown);
        assumptions.put("peer_set_source", peerSelection.peerSetSource());
        assumptions.put("relative_source_mode", peerSelection.sourceMode());
        assumptions.put("peer_selection_rule_version", peerSelection.ruleVersion());
        assumptions.put("peer_filter_summary", peerSelection.filterSummary());
        assumptions.put("peer_filter_metrics", peerSelection.filterMetrics());
        assumptions.put("industry_match_source", context.externalParameterSnapshot() == null ? null : context.externalParameterSnapshot().industryMatchSource());
        assumptions.put("industry_match_confidence", context.externalParameterSnapshot() == null ? null : context.externalParameterSnapshot().industryMatchConfidence());
        assumptions.put("industry_fallback_used", context.externalParameterSnapshot() != null && context.externalParameterSnapshot().industryFallbackUsed());
        assumptions.put("parameter_sources", parameterSources(context,
                Map.entry("target_ev_ebitda", "relative.target_ev_ebitda"),
                Map.entry("target_pe", "relative.target_pe")
        ));

        Map<String, Object> sensitivity = new LinkedHashMap<>();
        sensitivity.put("current_ev_ebitda", MathSupport.round(fundamentals.evEbitda()));
        sensitivity.put("current_pe", MathSupport.round(fundamentals.pe()));
        sensitivity.put("current_pb", MathSupport.round(fundamentals.pb()));
        sensitivity.put("anchor_factor", MathSupport.round(anchor));
        sensitivity.put("peer_set_size", peers.size());
        sensitivity.put("peer_median_ev_ebitda", peerMedianEvEbitda == null ? null : MathSupport.round(peerMedianEvEbitda));
        sensitivity.put("peer_median_pe", peerMedianPe == null ? null : MathSupport.round(peerMedianPe));
        sensitivity.put("peer_median_pb", peerMedianPb == null ? null : MathSupport.round(peerMedianPb));

        return new ValuationScenario(
                baseValue * (1.0 - band),
                baseValue,
                baseValue * (1.0 + band),
                "Relative valuation cross-checks current market multiples against persisted peer-set medians, with configured template targets kept as fallback anchors.",
                latestMarketDate(context),
                toJson(assumptions),
                toJson(sensitivity),
                anchors.isEmpty()
                        ? "No clean peer or vendor multiple series was available; relative valuation defaulted to price-neutral anchor."
                        : peers.isEmpty()
                        ? "Peer set is not yet available for this ticker after strict peer filters; relative valuation used configured template targets only."
                        : "Relative valuation used peer set tickers=" + peerTickers
                        + " basis=" + peerSelectionBasis
                        + " with " + components.size() + " market anchors."
        );
    }

    private ValuationScenario historicalMultipleValuation(UsValuationModelContext context) {
        StockSnapshot snapshot = context.snapshot();
        List<Double> closes = context.dailyHistory().stream()
                .map(UsMarketPriceDailyRecord::close)
                .filter(value -> value != null && value.doubleValue() > 0.0)
                .map(BigDecimal::doubleValue)
                .toList();
        List<Double> peSeries = context.marketSnapshots().stream()
                .map(UsMarketSnapshotRecord::peTtmVendor)
                .filter(value -> value != null && value.doubleValue() > 0.0)
                .map(BigDecimal::doubleValue)
                .toList();

        Double medianClose = median(closes);
        Double medianPe = median(peSeries);
        Double priceAnchor = medianClose == null ? null : MathSupport.clamp(medianClose / snapshot.price(), 0.75, 1.30);
        Double peAnchor = medianPe == null || snapshot.fundamentals().pe() <= 0.0
                ? null
                : MathSupport.clamp(medianPe / snapshot.fundamentals().pe(), 0.70, 1.30);

        List<Double> anchors = new ArrayList<>();
        if (priceAnchor != null) {
            anchors.add(priceAnchor);
        }
        if (peAnchor != null) {
            anchors.add(peAnchor);
        }

        double anchor = anchors.isEmpty()
                ? 1.0
                : anchors.stream().mapToDouble(Double::doubleValue).average().orElse(1.0);
        double baseValue = snapshot.price() * anchor;
        double band = closes.size() >= 20 ? 0.10 : closes.isEmpty() ? 0.15 : 0.12;

        Map<String, Object> assumptions = new LinkedHashMap<>();
        assumptions.put("source_tables", List.of("market_price_daily", "market_snapshot"));
        assumptions.put("daily_rows", closes.size());
        assumptions.put("snapshot_rows", peSeries.size());
        assumptions.put("median_close", medianClose);
        assumptions.put("median_pe_ttm", medianPe);

        Map<String, Object> sensitivity = new LinkedHashMap<>();
        sensitivity.put("price_anchor_factor", priceAnchor);
        sensitivity.put("multiple_anchor_factor", peAnchor);
        sensitivity.put("history_quality", closes.size() >= 20 ? "sufficient" : closes.isEmpty() ? "fallback" : "sparse");

        return new ValuationScenario(
                baseValue * (1.0 - band),
                baseValue,
                baseValue * (1.0 + band),
                "Historical multiple valuation anchors current price to persisted price and vendor-multiple history.",
                latestMarketDate(context),
                toJson(assumptions),
                toJson(sensitivity),
                closes.isEmpty() && peSeries.isEmpty()
                        ? "Historical market tables are still sparse; method currently uses a neutral anchor."
                        : "Historical multiple used persisted market tables with daily_rows=" + closes.size() + " and snapshot_rows=" + peSeries.size() + "."
        );
    }

    private ReverseScenario reverseDcfValuation(
            UsValuationModelContext context,
            DcfScenarioInputs baseInputs,
            double impliedGrowthFloor,
            double impliedGrowthCeiling
    ) {
        StockSnapshot snapshot = context.snapshot();
        double impliedGrowth;
        double targetEnterpriseValue;
        boolean structured = baseInputs.revenue > 0.0 && baseInputs.shares > 0.0;
        String notes;

        if (structured) {
            double marketCap = impliedMarketCap(context, baseInputs.shares);
            targetEnterpriseValue = marketCap + baseInputs.netDebt;
            impliedGrowth = solveImpliedGrowth(baseInputs, targetEnterpriseValue, impliedGrowthFloor, impliedGrowthCeiling);
            notes = "Reverse DCF solves the revenue CAGR required for current enterprise value under configured WACC and terminal growth.";
        } else {
            targetEnterpriseValue = snapshot.price();
            impliedGrowth = MathSupport.clamp(
                    (snapshot.fundamentals().pe() / 25.0) * 0.12
                            + baseInputs.wacc * 0.15
                            - baseInputs.currentFcfMargin * 0.08,
                    impliedGrowthFloor,
                    impliedGrowthCeiling
            );
            notes = "Structured FCFF inputs are incomplete; reverse DCF fell back to snapshot expectation-implied growth heuristic.";
        }

        double sustainableGrowth = MathSupport.clamp(
                Math.min(snapshot.fundamentals().revenueGrowth(), baseInputs.initialGrowth),
                impliedGrowthFloor,
                impliedGrowthCeiling
        );
        double expectationGap = sustainableGrowth - impliedGrowth;
        double factor = MathSupport.clamp(
                1.0 + expectationGap * 1.8 + (baseInputs.targetFcfMargin - baseInputs.currentFcfMargin) * 0.60,
                0.72,
                1.32
        );
        double baseValue = snapshot.price() * factor;
        double bearValue = snapshot.price() * MathSupport.clamp(factor - 0.10, 0.65, 1.28);
        double bullValue = snapshot.price() * MathSupport.clamp(factor + 0.10, 0.72, 1.38);

        String expectationLabel = expectationLabel(impliedGrowth, sustainableGrowth);
        Map<String, Object> reverseNotes = new LinkedHashMap<>();
        reverseNotes.put("structured_inputs", structured);
        reverseNotes.put("enterprise_value_target", MathSupport.round(targetEnterpriseValue));
        reverseNotes.put("revenue_ttm", MathSupport.round(baseInputs.revenue));
        reverseNotes.put("current_fcf_margin", MathSupport.round(baseInputs.currentFcfMargin));
        reverseNotes.put("target_fcf_margin", MathSupport.round(baseInputs.targetFcfMargin));
        reverseNotes.put("forecast_years", baseInputs.forecastYears);
        reverseNotes.put("sustainable_growth", MathSupport.round(sustainableGrowth));
        reverseNotes.put("growth_gap", MathSupport.round(expectationGap));
        reverseNotes.put("solver_range", List.of(MathSupport.round(impliedGrowthFloor), MathSupport.round(impliedGrowthCeiling)));

        Map<String, Object> assumptions = new LinkedHashMap<>();
        assumptions.put("source_tables", structured
                ? List.of("market_snapshot", "financial_standardized", "financial_derived_metrics")
                : List.of("stock_snapshot"));
        assumptions.put("effective_wacc", MathSupport.round(baseInputs.wacc));
        assumptions.put("terminal_growth", MathSupport.round(baseInputs.terminalGrowth));
        assumptions.put("implied_growth_floor", MathSupport.round(impliedGrowthFloor));
        assumptions.put("implied_growth_ceiling", MathSupport.round(impliedGrowthCeiling));
        assumptions.put("parameter_sources", parameterSources(context,
                Map.entry("wacc_base", "wacc.base"),
                Map.entry("wacc_rf", "wacc.rf"),
                Map.entry("wacc_erp", "wacc.erp"),
                Map.entry("wacc_beta", "wacc.beta"),
                Map.entry("terminal_growth", "terminal_growth.base")
        ));

        Map<String, Object> sensitivity = new LinkedHashMap<>();
        sensitivity.put("implied_revenue_cagr", MathSupport.round(impliedGrowth));
        sensitivity.put("sustainable_growth", MathSupport.round(sustainableGrowth));
        sensitivity.put("expectation_gap", MathSupport.round(expectationGap));

        UsReverseDcfAnalysis analysis = new UsReverseDcfAnalysis(
                MathSupport.round(impliedGrowth),
                MathSupport.round(resolveImpliedEbitdaMargin(context, baseInputs.targetFcfMargin)),
                MathSupport.round(baseInputs.targetFcfMargin),
                MathSupport.round(baseInputs.wacc),
                MathSupport.round(baseInputs.terminalGrowth),
                expectationLabel,
                toJson(reverseNotes)
        );

        ValuationScenario methodScenario = new ValuationScenario(
                bearValue,
                baseValue,
                bullValue,
                "Reverse DCF translates the gap between market-implied growth and sustainable growth into an expectation-adjusted value anchor.",
                latestMarketDate(context),
                toJson(assumptions),
                toJson(sensitivity),
                notes
        );

        return new ReverseScenario(methodScenario, analysis);
    }

    private List<MarketAdjustment> buildAdjustments(
            UsValuationModelContext context,
            double effectiveWacc,
            double terminalGrowth,
            UsRiskMatrixResult riskMatrixResult
    ) {
        List<MarketAdjustment> adjustments = new ArrayList<>();
        UsFinancialDerivedMetricRecord derived = context.latestDerived();
        UsDataQualityAuditRecord audit = context.latestAudit();

        if (riskMatrixResult.waccAdjustment() > 0.0) {
            adjustments.add(new MarketAdjustment(
                    "risk_matrix_wacc",
                    MathSupport.round(-riskMatrixResult.waccAdjustment()),
                    "PRD risk matrix increased the effective discount rate based on data quality, leverage, and execution risks."
            ));
        }
        if (riskMatrixResult.marginOfSafetyAdjustment() > 0.0) {
            adjustments.add(new MarketAdjustment(
                    "required_margin_of_safety",
                    MathSupport.round(-riskMatrixResult.marginOfSafetyAdjustment()),
                    "Risk matrix widened the required buy discount before a valuation can be considered actionable."
            ));
        }

        if (audit != null && audit.confidenceLevel() != null) {
            double confidence = audit.confidenceLevel().doubleValue();
            double impact = MathSupport.round((confidence - 0.80) * 0.10);
            if (Math.abs(impact) >= 0.005) {
                adjustments.add(new MarketAdjustment(
                        "data_quality_confidence",
                        impact,
                        "Data quality confidence shifts the blended fair value and feeds the effective valuation confidence."
                ));
            }
        }

        if (derived != null && derived.roic() != null) {
            double roic = derived.roic().doubleValue();
            double spread = roic - effectiveWacc;
            double impact = MathSupport.round(MathSupport.clamp(spread * 0.15, -0.03, 0.04));
            if (Math.abs(impact) >= 0.005) {
                adjustments.add(new MarketAdjustment(
                        "roic_spread",
                        impact,
                        "Positive ROIC-WACC spread supports higher fair value durability; negative spread implies weaker capital efficiency."
                ));
            }
        }

        if (derived != null && derived.netDebtToEbitda() != null) {
            double leverage = derived.netDebtToEbitda().doubleValue();
            if (leverage > 2.5) {
                adjustments.add(new MarketAdjustment(
                        "leverage_penalty",
                        MathSupport.round(-MathSupport.clamp((leverage - 2.5) * 0.015, 0.01, 0.04)),
                        "Higher leverage increases downside risk and widens valuation discipline."
                ));
            }
        }

        if (terminalGrowth >= effectiveWacc - 0.02) {
            adjustments.add(new MarketAdjustment(
                    "terminal_growth_guardrail",
                    -0.01,
                    "Terminal growth is close to discount rate and is slightly penalized to keep the DCF guardrail conservative."
            ));
        }

        return adjustments;
    }

    private List<String> buildRiskFlags(UsValuationModelContext context, double impliedGrowth, UsRiskMatrixResult riskMatrixResult) {
        List<String> flags = new ArrayList<>();
        StockFundamentals fundamentals = context.snapshot().fundamentals();
        if (fundamentals.pe() > 32.0) {
            flags.add("high_expectation_embedded");
        }
        if (fundamentals.sbcRatio() > 0.08) {
            flags.add("high_sbc_dilution");
        }
        if (context.latestDerived() != null
                && context.latestDerived().netDebtToEbitda() != null
                && context.latestDerived().netDebtToEbitda().doubleValue() > 3.0) {
            flags.add("leverage_elevated");
        }
        if (context.latestAudit() != null
                && context.latestAudit().confidenceLevel() != null
                && context.latestAudit().confidenceLevel().doubleValue() < 0.70) {
            flags.add("data_quality_below_preferred_threshold");
        }
        if (impliedGrowth > fundamentals.revenueGrowth() + 0.08) {
            flags.add("market_implied_growth_aggressive");
        }
        if (riskMatrixResult.valueTrapFlag()) {
            flags.add("value_trap_risk");
        }
        return flags;
    }

    private Map<String, Double> buildDrivers(
            UsValuationModelContext context,
            double effectiveWacc,
            double terminalGrowth,
            double targetFcfMargin,
            UsReverseDcfAnalysis reverseDcfAnalysis,
            UsRiskMatrixResult riskMatrixResult
    ) {
        Map<String, Double> drivers = new LinkedHashMap<>();
        drivers.put("revenue_growth", MathSupport.round(context.snapshot().fundamentals().revenueGrowth()));
        drivers.put("current_fcf_margin", MathSupport.round(resolveCurrentFcfMargin(context)));
        drivers.put("target_fcf_margin", MathSupport.round(targetFcfMargin));
        drivers.put("effective_wacc", MathSupport.round(effectiveWacc));
        drivers.put("terminal_growth", MathSupport.round(terminalGrowth));
        drivers.put("risk_wacc_adjustment", MathSupport.round(riskMatrixResult.waccAdjustment()));
        drivers.put("required_margin_of_safety", MathSupport.round(context.config().defaultMarginOfSafety() + riskMatrixResult.marginOfSafetyAdjustment()));
        drivers.put("implied_revenue_cagr", MathSupport.round(reverseDcfAnalysis.impliedRevenueCagr()));
        if (context.latestDerived() != null && context.latestDerived().roic() != null) {
            drivers.put("roic", MathSupport.round(context.latestDerived().roic().doubleValue()));
        }
        return drivers;
    }

    private double confidenceBase(UsValuationModelContext context, UsRiskMatrixResult riskMatrixResult) {
        double base = context.snapshot().fundamentals().dataCompleteness() * 0.55
                + MathSupport.clamp(1.0 - context.snapshot().fundamentals().earningsVolatility(), 0.15, 1.0) * 0.15
                + MathSupport.clamp(context.snapshot().fundamentals().liquidityScore(), 0.10, 1.0) * 0.10;
        if (context.latestAudit() != null && context.latestAudit().confidenceLevel() != null) {
            base += context.latestAudit().confidenceLevel().doubleValue() * 0.20;
        } else {
            base += 0.15;
        }
        base -= riskMatrixResult.confidencePenalty();
        return MathSupport.clamp(base, 0.45, 0.94);
    }

    private double resolveWacc(UsValuationModelContext context, Map<String, Double> customAssumptions) {
        double configured = parameter(context, "wacc.base", context.snapshot().fundamentals().wacc());
        double override = customAssumptions == null ? configured : customAssumptions.getOrDefault("wacc", configured);
        double adjusted = override;
        if (context.latestAudit() != null && context.latestAudit().confidenceLevel() != null) {
            double confidence = context.latestAudit().confidenceLevel().doubleValue();
            if (confidence < 0.70) {
                adjusted += 0.005;
            }
        }
        return clampWacc(context, adjusted);
    }

    private double resolveTerminalGrowth(
            UsValuationModelContext context,
            Map<String, Double> customAssumptions,
            double effectiveWacc
    ) {
        double configured = parameter(context, "terminal_growth.base", context.snapshot().fundamentals().terminalGrowth());
        double override = customAssumptions == null
                ? configured
                : customAssumptions.getOrDefault("terminal_growth", configured);
        double floor = parameter(context, "terminal_growth.floor", configured - 0.005);
        double ceiling = Math.min(parameter(context, "terminal_growth.ceiling", configured + 0.005), effectiveWacc - 0.015);
        return MathSupport.clamp(override, floor, ceiling);
    }

    private double resolveCurrentFcfMargin(UsValuationModelContext context) {
        if (context.latestDerived() != null && context.latestDerived().fcfMargin() != null) {
            return context.latestDerived().fcfMargin().doubleValue();
        }
        double latestStructuredMargin = latestRatio(
                context,
                UsFinancialStandardizedRecord::freeCashFlow,
                UsFinancialStandardizedRecord::revenue,
                "TTM",
                "FY",
                "Q"
        );
        if (latestStructuredMargin > 0.0) {
            return latestStructuredMargin;
        }
        double secMargin = secFcfMargin(context);
        if (secMargin > 0.0) {
            return secMargin;
        }
        return context.snapshot().fundamentals().fcfMargin();
    }

    private double resolveTargetFcfMargin(UsValuationModelContext context) {
        double configured = parameter(context, "dcf.target_fcf_margin", resolveCurrentFcfMargin(context));
        return MathSupport.clamp(configured, 0.04, 0.55);
    }

    private double resolveImpliedEbitdaMargin(UsValuationModelContext context, double targetFcfMargin) {
        if (context.latestDerived() != null && context.latestDerived().ebitMargin() != null) {
            return context.latestDerived().ebitMargin().doubleValue();
        }
        double secRevenue = secAnnualRevenue(context);
        if (secRevenue > 0.0 && context.secProfile() != null && context.secProfile().ebitdaProxy() != null) {
            return MathSupport.clamp(context.secProfile().ebitdaProxy() / secRevenue, 0.04, 0.60);
        }
        return MathSupport.clamp(targetFcfMargin * 1.30, 0.06, 0.60);
    }

    private double parameter(UsValuationModelContext context, String key, double fallback) {
        return context.config().numericParameters().getOrDefault(key, fallback);
    }

    private String parameterSource(UsValuationModelContext context, String key) {
        return context.config().parameterSources().get(key);
    }

    private Double peerEvEbitda(UsRelativePeerComparable peer) {
        double marketCap = decimalValue(peer.marketCap());
        double ebitda = decimalValue(peer.ebitda());
        if (marketCap <= 0.0 || ebitda <= 0.0) {
            return null;
        }
        return (marketCap + decimalValue(peer.netDebt())) / ebitda;
    }

    private String peerSelectionBasis(UsValuationModelContext context, List<UsRelativePeerComparable> peers) {
        Map<String, Long> breakdown = peerSelectionBreakdown(context, peers);
        if (breakdown.isEmpty()) {
            return "template_only";
        }
        return breakdown.size() == 1 ? breakdown.keySet().iterator().next() : "mixed";
    }

    private RelativePeerSelection selectRelativePeers(UsValuationModelContext context, int limit) {
        if (context.securityId() <= 0 || context.security() == null) {
            return new RelativePeerSelection(List.of(), 0, "no_security_context", null, List.of(), "template_only");
        }
        UsPeerUniverseRuleProfile rule = peerRule(context);
        int candidateLimit = Math.max(limit * rule.candidateLimitMultiplier(), 24);
        List<UsRelativePeerComparable> candidates = usSecurityMasterService.findRelativePeers(
                context.securityId(),
                context.security(),
                candidateLimit,
                rule.fetchMinFcfMargin(),
                rule.fetchMinRoic()
        );
        if (candidates.isEmpty()) {
            return new RelativePeerSelection(List.of(), 0, "no_candidates", rule.ruleVersion(), rule.filterMetrics(), "template_only");
        }

        for (String basis : rule.basisOrder()) {
            List<UsRelativePeerComparable> basisPeers = candidates.stream()
                    .filter(peer -> basis.equals(peerMatchBasis(context.security(), peer)))
                    .toList();
            if (basisPeers.isEmpty()) {
                continue;
            }
            List<UsRelativePeerComparable> filtered = applyStrictPeerFilters(context, basisPeers, limit);
            if (filtered.size() >= rule.minPeersRequired()) {
                return new RelativePeerSelection(
                        filtered,
                        candidates.size(),
                        "basis=" + basis + ", selected=" + filtered.size() + ", mode=strict, rule=" + rule.ruleId(),
                        rule.ruleVersion(),
                        rule.filterMetrics(),
                        "strict_peer_set"
                );
            }
        }

        for (String basis : rule.basisOrder()) {
            List<UsRelativePeerComparable> basisPeers = candidates.stream()
                    .filter(peer -> basis.equals(peerMatchBasis(context.security(), peer)))
                    .toList();
            if (!basisPeers.isEmpty()) {
                List<UsRelativePeerComparable> filtered = applyBasicPeerFilters(context, basisPeers, limit);
                if (filtered.size() >= 2) {
                    return new RelativePeerSelection(
                            filtered,
                            candidates.size(),
                            "basis=" + basis + ", selected=" + filtered.size() + ", mode=relaxed, rule=" + rule.ruleId(),
                            rule.ruleVersion(),
                            rule.filterMetrics(),
                            "relaxed_peer_set"
                    );
                }
            }
        }

        return new RelativePeerSelection(List.of(), candidates.size(), "no_clean_peer_set", rule.ruleVersion(), rule.filterMetrics(), "template_only");
    }

    private Map<String, Long> peerSelectionBreakdown(UsValuationModelContext context, List<UsRelativePeerComparable> peers) {
        if (peers.isEmpty() || context.security() == null) {
            return Map.of();
        }
        Map<String, Long> breakdown = new LinkedHashMap<>();
        for (UsRelativePeerComparable peer : peers) {
            String basis = peerMatchBasis(context.security(), peer);
            breakdown.merge(basis, 1L, Long::sum);
        }
        return breakdown;
    }

    private String peerMatchBasis(UsSecurityMaster security, UsRelativePeerComparable peer) {
        if (sameValue(security.industry(), peer.industry())) {
            return "industry";
        }
        if (sameValue(security.sectorTemplate(), peer.sectorTemplate())) {
            return "sector_template";
        }
        if (sameValue(security.companyType(), peer.companyType())) {
            return "company_type";
        }
        if (sameValue(security.sector(), peer.sector())) {
            return "sector";
        }
        return "other";
    }

    private UsPeerUniverseRuleProfile peerRule(UsValuationModelContext context) {
        return usPeerUniverseRulesService.resolve(context.security());
    }

    private boolean sameValue(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private List<UsRelativePeerComparable> applyStrictPeerFilters(
            UsValuationModelContext context,
            List<UsRelativePeerComparable> peers,
            int limit
    ) {
        UsPeerUniverseRuleProfile rule = peerRule(context);
        return peers.stream()
                .filter(peer -> hasUsableMultiple(peer))
                .filter(peer -> withinMarketCapBand(context, peer, rule.strictMarketCapMinRatio(), rule.strictMarketCapMaxRatio()))
                .filter(peer -> withinRevenueGrowthBand(context, peer, rule.strictRevenueGrowthTolerance(), rule.filterMetrics()))
                .filter(peer -> withinFcfMarginBand(context, peer, rule.strictFcfMarginTolerance(), rule.filterMetrics()))
                .filter(peer -> withinRoicBand(context, peer, rule.strictRoicTolerance(), rule.filterMetrics()))
                .filter(peer -> withinPeBand(context, peer, rule.strictPeTolerance(), rule.filterMetrics()))
                .filter(peer -> withinPbBand(context, peer, rule.strictPbTolerance(), rule.filterMetrics()))
                .sorted(Comparator.comparingDouble(peer -> peerDistance(context, peer, rule.filterMetrics())))
                .limit(limit)
                .toList();
    }

    private List<UsRelativePeerComparable> applyBasicPeerFilters(
            UsValuationModelContext context,
            List<UsRelativePeerComparable> peers,
            int limit
    ) {
        UsPeerUniverseRuleProfile rule = peerRule(context);
        return peers.stream()
                .filter(peer -> hasUsableMultiple(peer))
                .filter(peer -> withinMarketCapBand(context, peer, rule.relaxedMarketCapMinRatio(), rule.relaxedMarketCapMaxRatio()))
                .filter(peer -> withinRevenueGrowthBand(context, peer, rule.relaxedRevenueGrowthTolerance(), rule.filterMetrics()))
                .filter(peer -> withinFcfMarginBand(context, peer, rule.relaxedFcfMarginTolerance(), rule.filterMetrics()))
                .filter(peer -> withinRoicBand(context, peer, rule.relaxedRoicTolerance(), rule.filterMetrics()))
                .filter(peer -> withinPeBand(context, peer, rule.relaxedPeTolerance(), rule.filterMetrics()))
                .filter(peer -> withinPbBand(context, peer, rule.relaxedPbTolerance(), rule.filterMetrics()))
                .sorted(Comparator.comparingDouble(peer -> peerDistance(context, peer, rule.filterMetrics())))
                .limit(limit)
                .toList();
    }

    private boolean hasUsableMultiple(UsRelativePeerComparable peer) {
        return positive(peer.peTtm())
                || positive(peer.pb())
                || peerEvEbitda(peer) != null;
    }

    private boolean withinMarketCapBand(UsValuationModelContext context, UsRelativePeerComparable peer, double minRatio, double maxRatio) {
        double targetMarketCap = decimalValue(latestMarketCap(context));
        double peerMarketCap = decimalValue(peer.marketCap());
        if (targetMarketCap <= 0.0 || peerMarketCap <= 0.0) {
            return true;
        }
        double ratio = peerMarketCap / targetMarketCap;
        return ratio >= minRatio && ratio <= maxRatio;
    }

    private boolean withinFcfMarginBand(UsValuationModelContext context, UsRelativePeerComparable peer, Double tolerance, List<String> metrics) {
        if (!metrics.contains("fcf_margin") || tolerance == null) {
            return true;
        }
        Double targetFcfMargin = targetFcfMargin(context);
        double peerFcfMargin = decimalValue(peer.fcfMargin());
        if (targetFcfMargin == null || peerFcfMargin <= 0.0) {
            return true;
        }
        return Math.abs(peerFcfMargin - targetFcfMargin) <= tolerance;
    }

    private boolean withinRoicBand(UsValuationModelContext context, UsRelativePeerComparable peer, Double tolerance, List<String> metrics) {
        if (!metrics.contains("roic") || tolerance == null) {
            return true;
        }
        Double targetRoic = targetRoic(context);
        double peerRoic = decimalValue(peer.roic());
        if (targetRoic == null || peerRoic <= 0.0) {
            return true;
        }
        return Math.abs(peerRoic - targetRoic) <= tolerance;
    }

    private boolean withinRevenueGrowthBand(UsValuationModelContext context, UsRelativePeerComparable peer, Double tolerance, List<String> metrics) {
        if (!metrics.contains("revenue_growth") || tolerance == null) {
            return true;
        }
        Double targetRevenueGrowth = targetRevenueGrowth(context);
        if (targetRevenueGrowth == null || peer.revenueGrowthProxy() == null) {
            return true;
        }
        double peerRevenueGrowth = decimalValue(peer.revenueGrowthProxy());
        return Math.abs(peerRevenueGrowth - targetRevenueGrowth) <= tolerance;
    }

    private boolean withinPeBand(UsValuationModelContext context, UsRelativePeerComparable peer, Double tolerance, List<String> metrics) {
        if (!metrics.contains("pe") || tolerance == null) {
            return true;
        }
        Double targetPe = targetPe(context);
        if (targetPe == null || peer.peTtm() == null || peer.peTtm().doubleValue() <= 0.0) {
            return true;
        }
        return Math.abs(peer.peTtm().doubleValue() - targetPe) <= tolerance;
    }

    private boolean withinPbBand(UsValuationModelContext context, UsRelativePeerComparable peer, Double tolerance, List<String> metrics) {
        if (!metrics.contains("pb") || tolerance == null) {
            return true;
        }
        Double targetPb = targetPb(context);
        if (targetPb == null || peer.pb() == null || peer.pb().doubleValue() <= 0.0) {
            return true;
        }
        return Math.abs(peer.pb().doubleValue() - targetPb) <= tolerance;
    }

    private double peerDistance(UsValuationModelContext context, UsRelativePeerComparable peer, List<String> metrics) {
        double distance = 0.0;
        double targetMarketCap = decimalValue(latestMarketCap(context));
        double peerMarketCap = decimalValue(peer.marketCap());
        if (metrics.contains("market_cap") && targetMarketCap > 0.0 && peerMarketCap > 0.0) {
            distance += Math.abs(Math.log(peerMarketCap / targetMarketCap));
        }
        Double targetRevenueGrowth = targetRevenueGrowth(context);
        if (metrics.contains("revenue_growth") && targetRevenueGrowth != null && peer.revenueGrowthProxy() != null) {
            distance += Math.abs(decimalValue(peer.revenueGrowthProxy()) - targetRevenueGrowth);
        }
        Double targetFcfMargin = targetFcfMargin(context);
        if (metrics.contains("fcf_margin") && targetFcfMargin != null && positive(peer.fcfMargin())) {
            distance += Math.abs(decimalValue(peer.fcfMargin()) - targetFcfMargin);
        }
        Double targetRoic = targetRoic(context);
        if (metrics.contains("roic") && targetRoic != null && positive(peer.roic())) {
            distance += Math.abs(decimalValue(peer.roic()) - targetRoic);
        }
        Double targetPe = targetPe(context);
        if (metrics.contains("pe") && targetPe != null && positive(peer.peTtm())) {
            distance += Math.abs(decimalValue(peer.peTtm()) - targetPe) / Math.max(targetPe, 1.0);
        }
        Double targetPb = targetPb(context);
        if (metrics.contains("pb") && targetPb != null && positive(peer.pb())) {
            distance += Math.abs(decimalValue(peer.pb()) - targetPb) / Math.max(targetPb, 0.5);
        }
        return distance;
    }

    private BigDecimal latestMarketCap(UsValuationModelContext context) {
        return context.marketSnapshots().stream()
                .map(UsMarketSnapshotRecord::marketCapVendor)
                .filter(this::positive)
                .findFirst()
                .orElse(null);
    }

    private Double targetFcfMargin(UsValuationModelContext context) {
        if (positive(context.latestDerived() == null ? null : context.latestDerived().fcfMargin())) {
            return decimalValue(context.latestDerived().fcfMargin());
        }
        double fallback = context.snapshot().fundamentals().fcfMargin();
        return fallback > 0.0 ? fallback : null;
    }

    private Double targetRevenueGrowth(UsValuationModelContext context) {
        Double structured = structuredRevenueGrowth(context);
        if (structured != null) {
            return structured;
        }
        double fallback = context.snapshot().fundamentals().revenueGrowth();
        return Math.abs(fallback) > 0.0001 ? fallback : null;
    }

    private Double targetRoic(UsValuationModelContext context) {
        if (positive(context.latestDerived() == null ? null : context.latestDerived().roic())) {
            return decimalValue(context.latestDerived().roic());
        }
        return null;
    }

    private Double targetPe(UsValuationModelContext context) {
        double pe = context.snapshot().fundamentals().pe();
        return pe > 0.0 ? pe : null;
    }

    private Double targetPb(UsValuationModelContext context) {
        double pb = context.snapshot().fundamentals().pb();
        return pb > 0.0 ? pb : null;
    }

    private Double structuredRevenueGrowth(UsValuationModelContext context) {
        RevenuePoint latest = latestRevenuePoint(context);
        if (latest == null) {
            return null;
        }
        RevenuePoint previous = previousRevenuePoint(context, latest);
        if (previous == null || previous.revenue() == null || previous.revenue().doubleValue() <= 0.0) {
            return null;
        }
        return latest.revenue().doubleValue() / previous.revenue().doubleValue() - 1.0;
    }

    private RevenuePoint latestRevenuePoint(UsValuationModelContext context) {
        return context.financials().stream()
                .filter(record -> positive(record.revenue()) && ("TTM".equals(record.periodType()) || "FY".equals(record.periodType())))
                .map(record -> new RevenuePoint(record.periodType(), record.periodEnd(), record.revenue()))
                .findFirst()
                .orElse(null);
    }

    private RevenuePoint previousRevenuePoint(UsValuationModelContext context, RevenuePoint latest) {
        return context.financials().stream()
                .filter(record -> latest.periodType().equals(record.periodType()))
                .filter(record -> positive(record.revenue()))
                .filter(record -> latest.periodEnd() != null && record.periodEnd() != null && record.periodEnd().isBefore(latest.periodEnd()))
                .map(record -> new RevenuePoint(record.periodType(), record.periodEnd(), record.revenue()))
                .findFirst()
                .orElse(null);
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.doubleValue() > 0.0;
    }

    private record RelativePeerSelection(
            List<UsRelativePeerComparable> peers,
            int candidateCount,
            String filterSummary,
            String ruleVersion,
            List<String> filterMetrics,
            String selectionMode
    ) {
    }

    public record RelativePeerSelectionView(
            List<UsRelativePeerComparable> peers,
            int candidateCount,
            String selectionBasis,
            Map<String, Long> selectionBreakdown,
            String filterSummary,
            String ruleVersion,
            List<String> filterMetrics,
            String selectionMode,
            String sourceMode,
            String peerSetSource
    ) {
    }

    private record RevenuePoint(
            String periodType,
            LocalDate periodEnd,
            BigDecimal revenue
    ) {
    }

    @SafeVarargs
    private final Map<String, String> parameterSources(UsValuationModelContext context, Map.Entry<String, String>... aliases) {
        Map<String, String> sources = new LinkedHashMap<>();
        for (Map.Entry<String, String> alias : aliases) {
            String source = parameterSource(context, alias.getValue());
            if (source != null && !source.isBlank()) {
                sources.put(alias.getKey(), source);
            }
        }
        return sources;
    }

    private double clampWacc(UsValuationModelContext context, double value) {
        double configured = parameter(context, "wacc.base", context.snapshot().fundamentals().wacc());
        double floor = parameter(context, "wacc.floor", configured - 0.01);
        double ceiling = parameter(context, "wacc.ceiling", configured + 0.03);
        return MathSupport.clamp(value, floor, ceiling);
    }

    private LocalDate latestMarketDate(UsValuationModelContext context) {
        if (!context.dailyHistory().isEmpty() && context.dailyHistory().get(0).tradeDate() != null) {
            return context.dailyHistory().get(0).tradeDate();
        }
        if (!context.marketSnapshots().isEmpty() && context.marketSnapshots().get(0).snapshotTime() != null) {
            return context.marketSnapshots().get(0).snapshotTime().atZone(ZoneOffset.UTC).toLocalDate();
        }
        return LocalDate.now();
    }

    private UsFinancialStandardizedRecord firstByPeriodType(List<UsFinancialStandardizedRecord> items, String periodType) {
        return items.stream()
                .filter(item -> periodType.equalsIgnoreCase(item.periodType()))
                .findFirst()
                .orElse(null);
    }

    private double equityPerShareFromFcff(DcfScenarioInputs inputs) {
        double enterpriseValue = enterpriseValue(inputs, inputs.initialGrowth);
        double equityValue = enterpriseValue - inputs.netDebt;
        return Math.max(equityValue, 0.0) / inputs.shares;
    }

    private double enterpriseValue(DcfScenarioInputs inputs, double constantGrowth) {
        double revenue = inputs.revenue;
        double presentValue = 0.0;

        for (int year = 1; year <= inputs.forecastYears; year++) {
            double progress = year / (double) inputs.forecastYears;
            double growth = MathSupport.clamp(
                    interpolate(constantGrowth, inputs.growthFloor, progress),
                    -0.20,
                    0.50
            );
            double margin = MathSupport.clamp(
                    interpolate(inputs.currentFcfMargin, inputs.targetFcfMargin, progress),
                    0.01,
                    0.60
            );
            revenue *= 1.0 + growth;
            double fcff = revenue * margin;
            presentValue += fcff / Math.pow(1.0 + inputs.wacc, year);
        }

        double terminalMargin = MathSupport.clamp(inputs.targetFcfMargin, 0.01, 0.60);
        double terminalFcff = revenue * terminalMargin * (1.0 + inputs.terminalGrowth);
        double terminalValue = terminalFcff / Math.max(inputs.wacc - inputs.terminalGrowth, 0.01);
        return presentValue + terminalValue / Math.pow(1.0 + inputs.wacc, inputs.forecastYears);
    }

    private double solveImpliedGrowth(
            DcfScenarioInputs inputs,
            double targetEnterpriseValue,
            double growthFloor,
            double growthCeiling
    ) {
        double low = growthFloor;
        double high = growthCeiling;
        double lowValue = enterpriseValue(inputs, low);
        double highValue = enterpriseValue(inputs, high);

        if (targetEnterpriseValue <= lowValue) {
            return low;
        }
        if (targetEnterpriseValue >= highValue) {
            return high;
        }

        for (int i = 0; i < 50; i++) {
            double mid = (low + high) / 2.0;
            double value = enterpriseValue(inputs, mid);
            if (value < targetEnterpriseValue) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2.0;
    }

    private double impliedMarketCap(UsValuationModelContext context, double shares) {
        if (!context.marketSnapshots().isEmpty()) {
            UsMarketSnapshotRecord snapshot = context.marketSnapshots().get(0);
            if (snapshot.marketCapVendor() != null && snapshot.marketCapVendor().doubleValue() > 0.0) {
                return snapshot.marketCapVendor().doubleValue();
            }
            if (snapshot.lastPrice() != null && snapshot.lastPrice().doubleValue() > 0.0) {
                return snapshot.lastPrice().doubleValue() * shares;
            }
        }
        return context.snapshot().price() * shares;
    }

    private double impliedSharesFromMarketSnapshot(UsValuationModelContext context) {
        if (context.marketSnapshots().isEmpty()) {
            return 0.0;
        }
        UsMarketSnapshotRecord snapshot = context.marketSnapshots().get(0);
        if (snapshot.marketCapVendor() == null || snapshot.lastPrice() == null || snapshot.lastPrice().doubleValue() <= 0.0) {
            return 0.0;
        }
        return snapshot.marketCapVendor().doubleValue() / snapshot.lastPrice().doubleValue();
    }

    private double latestPositiveFinancialValue(
            UsValuationModelContext context,
            Function<UsFinancialStandardizedRecord, BigDecimal> extractor,
            String... preferredPeriodTypes
    ) {
        if (preferredPeriodTypes != null) {
            for (String periodType : preferredPeriodTypes) {
                for (UsFinancialStandardizedRecord record : context.financials()) {
                    if (!periodType.equalsIgnoreCase(record.periodType())) {
                        continue;
                    }
                    double value = decimalValue(extractor.apply(record));
                    if (value > 0.0) {
                        return value;
                    }
                }
            }
        }
        for (UsFinancialStandardizedRecord record : context.financials()) {
            double value = decimalValue(extractor.apply(record));
            if (value > 0.0) {
                return value;
            }
        }
        return 0.0;
    }

    private double latestRatio(
            UsValuationModelContext context,
            Function<UsFinancialStandardizedRecord, BigDecimal> numeratorExtractor,
            Function<UsFinancialStandardizedRecord, BigDecimal> denominatorExtractor,
            String... preferredPeriodTypes
    ) {
        if (preferredPeriodTypes != null) {
            for (String periodType : preferredPeriodTypes) {
                for (UsFinancialStandardizedRecord record : context.financials()) {
                    if (!periodType.equalsIgnoreCase(record.periodType())) {
                        continue;
                    }
                    double numerator = decimalValue(numeratorExtractor.apply(record));
                    double denominator = decimalValue(denominatorExtractor.apply(record));
                    if (numerator > 0.0 && denominator > 0.0) {
                        return numerator / denominator;
                    }
                }
            }
        }
        for (UsFinancialStandardizedRecord record : context.financials()) {
            double numerator = decimalValue(numeratorExtractor.apply(record));
            double denominator = decimalValue(denominatorExtractor.apply(record));
            if (numerator > 0.0 && denominator > 0.0) {
                return numerator / denominator;
            }
        }
        return 0.0;
    }

    private double resolveNetDebt(UsValuationModelContext context) {
        for (UsFinancialStandardizedRecord record : context.financials()) {
            if (record.netDebt() != null) {
                return record.netDebt().doubleValue();
            }
            if (record.totalDebt() != null || record.cash() != null) {
                return decimalValue(record.totalDebt()) - decimalValue(record.cash());
            }
        }
        if (context.secProfile() != null) {
            return positive(context.secProfile().totalDebt() == null ? 0.0 : context.secProfile().totalDebt())
                    - positive(context.secProfile().cash() == null ? 0.0 : context.secProfile().cash());
        }
        return 0.0;
    }

    private double secAnnualRevenue(UsValuationModelContext context) {
        return context.secProfile() == null || context.secProfile().annualRevenue() == null
                ? 0.0
                : context.secProfile().annualRevenue();
    }

    private double secSharesOutstanding(UsValuationModelContext context) {
        return context.secProfile() == null || context.secProfile().sharesOutstanding() == null
                ? 0.0
                : context.secProfile().sharesOutstanding();
    }

    private double secFcfMargin(UsValuationModelContext context) {
        if (context.secProfile() == null
                || context.secProfile().annualRevenue() == null
                || context.secProfile().annualRevenue() <= 0.0
                || context.secProfile().annualOperatingCashFlow() == null) {
            return 0.0;
        }
        double capex = context.secProfile().annualCapex() == null ? 0.0 : context.secProfile().annualCapex();
        double fcff = context.secProfile().annualOperatingCashFlow() - Math.abs(capex);
        if (fcff <= 0.0) {
            return 0.0;
        }
        return fcff / context.secProfile().annualRevenue();
    }

    private double defaultRelativeEvEbitda(StockFundamentals fundamentals) {
        if (fundamentals.evEbitda() <= 0.0) {
            return 16.0;
        }
        return fundamentals.evEbitda();
    }

    private double defaultRelativePe(StockFundamentals fundamentals) {
        if (fundamentals.pe() <= 0.0) {
            return 20.0;
        }
        return fundamentals.pe();
    }

    private String expectationLabel(double impliedGrowth, double sustainableGrowth) {
        double gap = impliedGrowth - sustainableGrowth;
        if (gap > 0.08) {
            return "aggressive";
        }
        if (gap < -0.04) {
            return "conservative";
        }
        return "balanced";
    }

    private double positive(double... values) {
        for (double value : values) {
            if (value > 0.0) {
                return value;
            }
        }
        return 0.0;
    }

    private double decimalValue(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private double interpolate(double start, double end, double progress) {
        return start + (end - start) * MathSupport.clamp(progress, 0.0, 1.0);
    }

    private Double median(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
        }
        return sorted.get(middle);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize configured valuation payload.", ex);
        }
    }

    private record DcfScenarioInputs(
            double revenue,
            double shares,
            double netDebt,
            double wacc,
            double terminalGrowth,
            int forecastYears,
            double initialGrowth,
            double growthFloor,
            double targetFcfMargin,
            double currentFcfMargin
    ) {
        private DcfScenarioInputs withScenario(
                double scenarioWacc,
                double scenarioTerminalGrowth,
                double scenarioInitialGrowth,
                double scenarioGrowthFloor,
                double scenarioTargetMargin
        ) {
            return new DcfScenarioInputs(
                    revenue,
                    shares,
                    netDebt,
                    scenarioWacc,
                    scenarioTerminalGrowth,
                    forecastYears,
                    scenarioInitialGrowth,
                    scenarioGrowthFloor,
                    scenarioTargetMargin,
                    currentFcfMargin
            );
        }
    }

    private record ValuationScenario(
            double bearValue,
            double baseValue,
            double bullValue,
            String rationale,
            LocalDate inputSnapshotDate,
            String assumptionsJson,
            String sensitivityJson,
            String notes
    ) {
    }

    private record ReverseScenario(
            ValuationScenario methodScenario,
            UsReverseDcfAnalysis analysis
    ) {
    }
}
