package com.fairvalue.engine.us;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairvalue.engine.api.dto.us.UsFairValueRange;
import com.fairvalue.engine.api.dto.us.UsMethodOutput;
import com.fairvalue.engine.api.dto.us.UsRiskItem;
import com.fairvalue.engine.api.dto.us.UsScenarioOutput;
import com.fairvalue.engine.api.dto.us.UsValuationDecisionResponse;
import com.fairvalue.engine.api.dto.us.UsValuationExplanationResponse;
import com.fairvalue.engine.api.dto.us.UsValuationReportResponse;
import com.fairvalue.engine.api.dto.us.UsValuationRunRequest;
import com.fairvalue.engine.api.dto.us.UsValuationSummaryResponse;
import com.fairvalue.engine.domain.StockSnapshot;
import com.fairvalue.engine.repository.FinancialDerivedMetricsRepository;
import com.fairvalue.engine.repository.MarketPriceDailyRepository;
import com.fairvalue.engine.repository.MarketSnapshotRepository;
import com.fairvalue.engine.repository.ReportBlocksRepository;
import com.fairvalue.engine.repository.ReverseDcfResultsRepository;
import com.fairvalue.engine.repository.RiskScoresRepository;
import com.fairvalue.engine.repository.ScenarioResultsRepository;
import com.fairvalue.engine.repository.ValuationLatestSnapshotRepository;
import com.fairvalue.engine.repository.ValuationMethodResultsRepository;
import com.fairvalue.engine.repository.ValuationRunsRepository;
import com.fairvalue.engine.valuation.ValuationResult;
import com.fairvalue.engine.valuation.strategy.MathSupport;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;

@Service
public class UsValuationPersistenceService {
    private final UsSecurityMasterService usSecurityMasterService;
    private final FinancialDerivedMetricsRepository financialDerivedMetricsRepository;
    private final MarketPriceDailyRepository marketPriceDailyRepository;
    private final MarketSnapshotRepository marketSnapshotRepository;
    private final ValuationRunsRepository valuationRunsRepository;
    private final ValuationLatestSnapshotRepository valuationLatestSnapshotRepository;
    private final ValuationMethodResultsRepository valuationMethodResultsRepository;
    private final ScenarioResultsRepository scenarioResultsRepository;
    private final ReverseDcfResultsRepository reverseDcfResultsRepository;
    private final RiskScoresRepository riskScoresRepository;
    private final ReportBlocksRepository reportBlocksRepository;
    private final ObjectMapper objectMapper;

    public UsValuationPersistenceService(
            UsSecurityMasterService usSecurityMasterService,
            FinancialDerivedMetricsRepository financialDerivedMetricsRepository,
            MarketPriceDailyRepository marketPriceDailyRepository,
            MarketSnapshotRepository marketSnapshotRepository,
            ValuationRunsRepository valuationRunsRepository,
            ValuationLatestSnapshotRepository valuationLatestSnapshotRepository,
            ValuationMethodResultsRepository valuationMethodResultsRepository,
            ScenarioResultsRepository scenarioResultsRepository,
            ReverseDcfResultsRepository reverseDcfResultsRepository,
            RiskScoresRepository riskScoresRepository,
            ReportBlocksRepository reportBlocksRepository,
            ObjectMapper objectMapper
    ) {
        this.usSecurityMasterService = usSecurityMasterService;
        this.financialDerivedMetricsRepository = financialDerivedMetricsRepository;
        this.marketPriceDailyRepository = marketPriceDailyRepository;
        this.marketSnapshotRepository = marketSnapshotRepository;
        this.valuationRunsRepository = valuationRunsRepository;
        this.valuationLatestSnapshotRepository = valuationLatestSnapshotRepository;
        this.valuationMethodResultsRepository = valuationMethodResultsRepository;
        this.scenarioResultsRepository = scenarioResultsRepository;
        this.reverseDcfResultsRepository = reverseDcfResultsRepository;
        this.riskScoresRepository = riskScoresRepository;
        this.reportBlocksRepository = reportBlocksRepository;
        this.objectMapper = objectMapper;
    }

    public OptionalLong persistRun(
            String rawTicker,
            UsValuationRunRequest request,
            StockSnapshot snapshot,
            ValuationResult base,
            List<UsConfiguredMethodValuation> selectedMethods,
            List<UsMethodOutput> methodOutputs,
            List<UsScenarioOutput> scenarios,
            List<UsRiskItem> risks,
            Map<String, String> explanationBlocks,
            UsFairValueRange fairValueRange,
            double blendedIntrinsicValue,
            double confidenceLevel,
            double marginOfSafety,
            UsReverseDcfAnalysis reverseDcfAnalysis,
            String finalVerdict,
            UsSecurityMaster classified,
            String runMode,
            UsValuationSummaryResponse summary,
            UsValuationDecisionResponse decision,
            UsValuationExplanationResponse explanation,
            UsValuationReportResponse report,
            Double qualityScore,
            Double dataQualityScore
    ) {
        Long securityId = classified != null && classified.id() != null
                ? classified.id()
                : usSecurityMasterService.resolveSecurityId(rawTicker).orElse(null);
        if (securityId == null) {
            return OptionalLong.empty();
        }

        List<UsMarketPriceDailyRecord> dailyHistory = marketPriceDailyRepository.findLatestBySecurityId(securityId, 252);
        List<UsMarketSnapshotRecord> snapshotHistory = marketSnapshotRepository.findLatestBySecurityId(securityId, 252);
        UsFinancialDerivedMetricRecord latestDerived = financialDerivedMetricsRepository.findLatestBySecurityId(securityId, 1)
                .stream()
                .findFirst()
                .orElse(null);

        UsFairValueRange buyZone = new UsFairValueRange(
                MathSupport.round(fairValueRange.low() * 0.85),
                MathSupport.round(fairValueRange.low()),
                MathSupport.round(fairValueRange.mid() * 0.92)
        );
        UsFairValueRange holdZone = new UsFairValueRange(
                MathSupport.round(fairValueRange.low()),
                MathSupport.round(fairValueRange.mid()),
                MathSupport.round(fairValueRange.high())
        );
        UsFairValueRange avoidZone = new UsFairValueRange(
                MathSupport.round(fairValueRange.high()),
                MathSupport.round(fairValueRange.high() * 1.08),
                MathSupport.round(fairValueRange.high() * 1.18)
        );

        long runId = valuationRunsRepository.insert(new UsValuationRunRecord(
                securityId,
                Instant.now(),
                runMode,
                snapshot.price(),
                fairValueRange.low(),
                fairValueRange.mid(),
                fairValueRange.high(),
                blendedIntrinsicValue,
                confidenceLevel,
                marginOfSafety,
                reverseDcfAnalysis == null ? null : reverseDcfAnalysis.impliedExpectationLabel(),
                classified == null ? null : classified.sectorTemplate(),
                classified == null ? null : classified.companyType(),
                blendedIntrinsicValue,
                finalVerdict,
                buyZone.low(),
                buyZone.high(),
                holdZone.low(),
                holdZone.high(),
                avoidZone.low(),
                avoidZone.high(),
                report == null ? "{}" : toJson(report)
        ));

        valuationMethodResultsRepository.insertAll(buildMethodResults(
                runId,
                securityId,
                selectedMethods
        ));
        scenarioResultsRepository.insertAll(buildScenarioResults(runId, scenarios, snapshot, latestDerived));
        reverseDcfResultsRepository.upsert(buildReverseDcfResult(runId, reverseDcfAnalysis));
        riskScoresRepository.insertAll(buildRiskScores(runId, securityId, risks));
        reportBlocksRepository.insertAll(buildReportBlocks(runId, explanationBlocks, risks, methodOutputs, scenarios));
        valuationLatestSnapshotRepository.upsert(new UsValuationLatestSnapshotRecord(
                securityId,
                snapshot.market().name(),
                runId,
                Instant.now(),
                snapshot.price(),
                fairValueRange.low(),
                fairValueRange.mid(),
                fairValueRange.high(),
                MathSupport.round(fairValueRange.mid() / Math.max(snapshot.price(), 0.1) - 1.0),
                confidenceLevel,
                marginOfSafety,
                finalVerdict,
                reverseDcfAnalysis == null ? null : reverseDcfAnalysis.impliedExpectationLabel(),
                decision != null && decision.valueTrapFlag(),
                classified == null ? null : classified.sectorTemplate(),
                classified == null ? null : classified.companyType(),
                qualityScore,
                dataQualityScore,
                snapshot.dataVersion(),
                summary == null ? null : summary.priceAsOf(),
                summary == null ? null : summary.priceFreshnessDays(),
                summary == null ? null : summary.priceSourceType(),
                summary != null && Boolean.TRUE.equals(summary.rankable()),
                summary == null ? null : summary.valuationStatus(),
                summary == null ? null : summary.exclusionReason(),
                stringFromMap(decision == null ? null : decision.sourceAttribution(), "industryMatchSource", "industry_match_source"),
                doubleFromMap(decision == null ? null : decision.sourceAttribution(), "industryMatchConfidence", "industry_match_confidence"),
                booleanFromMap(decision == null ? null : decision.sourceAttribution(), "industryFallbackUsed", "industry_fallback_used"),
                summary == null ? "{}" : toJson(summary),
                report == null ? "{}" : toJson(report),
                decision == null ? "{}" : toJson(decision.sourceAttribution())
        ));
        return OptionalLong.of(runId);
    }

    private List<UsValuationMethodResultRecord> buildMethodResults(
            long runId,
            long securityId,
            List<UsConfiguredMethodValuation> selectedMethods
    ) {
        return selectedMethods.stream()
                .map(method -> new UsValuationMethodResultRecord(
                        runId,
                        securityId,
                        method.method(),
                        method.bearValue(),
                        method.baseValue(),
                        method.bullValue(),
                        MathSupport.round(method.weight()),
                        method.primaryMethod(),
                        method.inputSnapshotDate(),
                        emptyJson(method.assumptionsJson()),
                        emptyJson(method.sensitivityJson()),
                        method.notes() == null || method.notes().isBlank() ? method.rationale() : method.notes()
                ))
                .sorted(java.util.Comparator.comparing(UsValuationMethodResultRecord::methodName))
                .toList();
    }

    private List<UsScenarioResultRecord> buildScenarioResults(
            long runId,
            List<UsScenarioOutput> scenarios,
            StockSnapshot snapshot,
            UsFinancialDerivedMetricRecord latestDerived
    ) {
        double baseGrowth = snapshot.fundamentals().revenueGrowth();
        BigDecimal baseEbitdaMargin = latestDerived != null && latestDerived.ebitMargin() != null
                ? latestDerived.ebitMargin()
                : decimal(MathSupport.clamp(snapshot.fundamentals().fcfMargin() * 1.35, 0.05, 0.45));
        BigDecimal baseFcfMargin = latestDerived != null && latestDerived.fcfMargin() != null
                ? latestDerived.fcfMargin()
                : decimal(snapshot.fundamentals().fcfMargin());

        List<UsScenarioResultRecord> records = new ArrayList<>();
        for (UsScenarioOutput scenario : scenarios) {
            double growthShift = switch (scenario.scenario().toLowerCase(Locale.ROOT)) {
                case "bear" -> -0.03;
                case "bull" -> 0.03;
                default -> 0.0;
            };
            double marginShift = switch (scenario.scenario().toLowerCase(Locale.ROOT)) {
                case "bear" -> -0.02;
                case "bull" -> 0.02;
                default -> 0.0;
            };

            Map<String, Object> notes = new LinkedHashMap<>();
            notes.put("upside", scenario.upside());
            notes.put("valuation_low", scenario.fairValueLow());
            notes.put("valuation_high", scenario.fairValueHigh());

            records.add(new UsScenarioResultRecord(
                    runId,
                    scenario.scenario(),
                    scenario.probability(),
                    scenario.targetPrice(),
                    decimal(MathSupport.clamp(baseGrowth + growthShift, -0.20, 0.45)),
                    adjust(baseEbitdaMargin, marginShift),
                    adjust(baseFcfMargin, marginShift),
                    toJson(notes)
            ));
        }
        return records;
    }

    private UsReverseDcfResultRecord buildReverseDcfResult(long runId, UsReverseDcfAnalysis analysis) {
        UsReverseDcfAnalysis effective = analysis == null
                ? new UsReverseDcfAnalysis(0.0, 0.0, 0.0, 0.0, 0.0, "balanced", "{}")
                : analysis;
        return new UsReverseDcfResultRecord(
                runId,
                decimal(effective.impliedRevenueCagr()),
                decimal(effective.impliedEbitdaMargin()),
                decimal(effective.impliedFcfMargin()),
                decimal(effective.terminalGrowth()),
                decimal(effective.effectiveWacc()),
                effective.impliedExpectationLabel(),
                emptyJson(effective.notesJson())
        );
    }

    private List<UsRiskScoreRecord> buildRiskScores(long runId, long securityId, List<UsRiskItem> risks) {
        return risks.stream()
                .map(risk -> {
                    double probability = riskLevel(risk.probability());
                    double impact = riskLevel(risk.impact());
                    return new UsRiskScoreRecord(
                            securityId,
                            runId,
                            risk.riskType(),
                            probability,
                            impact,
                            MathSupport.round(probability * impact),
                            normalizeAdjustmentType(risk.adjustmentType()),
                            risk.adjustment(),
                            risk.note()
                    );
                })
                .toList();
    }

    private List<UsReportBlockRecord> buildReportBlocks(
            long runId,
            Map<String, String> explanationBlocks,
            List<UsRiskItem> risks,
            List<UsMethodOutput> methodOutputs,
            List<UsScenarioOutput> scenarios
    ) {
        List<UsReportBlockRecord> records = new ArrayList<>();
        int order = 1;
        for (Map.Entry<String, String> entry : explanationBlocks.entrySet()) {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("text", entry.getValue());
            records.add(new UsReportBlockRecord(
                    runId,
                    entry.getKey(),
                    toJson(block),
                    order++
            ));
        }
        records.add(new UsReportBlockRecord(runId, "risk_matrix", toJson(Map.of("items", risks)), order++));
        records.add(new UsReportBlockRecord(runId, "valuation_breakdown", toJson(Map.of("methods", methodOutputs)), order++));
        records.add(new UsReportBlockRecord(runId, "scenario_matrix", toJson(Map.of("scenarios", scenarios)), order));
        return records;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize valuation payload.", ex);
        }
    }

    private String emptyJson(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }

    private double riskLevel(String label) {
        if (label == null) {
            return 0.0;
        }
        return switch (label.toLowerCase(Locale.ROOT)) {
            case "high" -> 0.85;
            case "medium" -> 0.55;
            default -> 0.20;
        };
    }

    private String normalizeAdjustmentType(String adjustmentType) {
        if (adjustmentType == null) {
            return "scenario_weight";
        }
        return switch (adjustmentType.toLowerCase(Locale.ROOT)) {
            case "margin_of_safety" -> "mos";
            case "wacc", "scenario_weight", "mos" -> adjustmentType.toLowerCase(Locale.ROOT);
            default -> "scenario_weight";
        };
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal adjust(BigDecimal base, double delta) {
        return decimal(MathSupport.clamp(safeDouble(base) + delta, -0.20, 0.60));
    }

    private Double median(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
        }
        return sorted.get(mid);
    }

    private double safeDouble(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private String stringFromMap(Map<String, Object> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private Double doubleFromMap(Map<String, Object> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value != null) {
                try {
                    return Double.parseDouble(value.toString());
                } catch (Exception ignored) {
                    // continue
                }
            }
        }
        return null;
    }

    private boolean booleanFromMap(Map<String, Object> source, String... keys) {
        if (source == null) {
            return false;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value != null) {
                return Boolean.parseBoolean(value.toString());
            }
        }
        return false;
    }

}
