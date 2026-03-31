package com.fairvalue.engine.us;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairvalue.engine.repository.FinancialStandardizedRepository;
import com.fairvalue.engine.repository.SourceDocumentFactRawRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UsFinancialStandardizationService {
    private static final List<String> REVENUE_CONCEPTS = List.of(
            "RevenueFromContractWithCustomerExcludingAssessedTax",
            "Revenues",
            "SalesRevenueNet"
    );
    private static final List<String> GROSS_PROFIT_CONCEPTS = List.of("GrossProfit");
    private static final List<String> EBIT_CONCEPTS = List.of("OperatingIncomeLoss");
    private static final List<String> DEPRECIATION_CONCEPTS = List.of(
            "DepreciationDepletionAndAmortization",
            "DepreciationAmortizationAndAccretionNet",
            "Depreciation"
    );
    private static final List<String> NET_INCOME_CONCEPTS = List.of("NetIncomeLoss", "ProfitLoss");
    private static final List<String> OCF_CONCEPTS = List.of(
            "NetCashProvidedByUsedInOperatingActivities",
            "NetCashProvidedByUsedInOperatingActivitiesContinuingOperations"
    );
    private static final List<String> CAPEX_CONCEPTS = List.of(
            "PaymentsToAcquirePropertyPlantAndEquipment",
            "CapitalExpendituresIncurredButNotYetPaid",
            "PropertyPlantAndEquipmentAdditions"
    );
    private static final List<String> CASH_CONCEPTS = List.of(
            "CashAndCashEquivalentsAtCarryingValue",
            "CashCashEquivalentsRestrictedCashAndRestrictedCashEquivalents"
    );
    private static final List<String> SHORT_TERM_DEBT_CONCEPTS = List.of(
            "LongTermDebtAndCapitalLeaseObligationsCurrent",
            "LongTermDebtCurrent",
            "ShortTermBorrowings"
    );
    private static final List<String> LONG_TERM_DEBT_CONCEPTS = List.of(
            "LongTermDebtAndCapitalLeaseObligations",
            "LongTermDebtAndFinanceLeaseObligations",
            "LongTermDebtNoncurrent",
            "LongTermDebt"
    );
    private static final List<String> EQUITY_CONCEPTS = List.of(
            "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest",
            "StockholdersEquity"
    );
    private static final List<String> TOTAL_ASSETS_CONCEPTS = List.of("Assets");
    private static final List<String> TOTAL_LIABILITIES_CONCEPTS = List.of("Liabilities");
    private static final List<String> DILUTED_SHARES_CONCEPTS = List.of(
            "EntityCommonStockSharesOutstanding",
            "WeightedAverageNumberOfDilutedSharesOutstanding",
            "WeightedAverageNumberOfShareOutstandingDiluted"
    );
    private static final List<String> BASIC_SHARES_CONCEPTS = List.of(
            "EntityCommonStockSharesOutstanding",
            "WeightedAverageNumberOfSharesOutstandingBasic",
            "WeightedAverageNumberOfShareOutstandingBasicAndDiluted"
    );
    private static final List<String> SBC_CONCEPTS = List.of(
            "ShareBasedCompensation",
            "AllocatedShareBasedCompensationExpense"
    );
    private static final List<String> LEASE_LIABILITY_CONCEPTS = List.of(
            "OperatingLeaseLiability",
            "OperatingLeaseLiabilityCurrent",
            "OperatingLeaseLiabilityNoncurrent",
            "FinanceLeaseLiability",
            "FinanceLeaseLiabilityCurrent",
            "FinanceLeaseLiabilityNoncurrent"
    );
    private static final List<String> PENSION_LIABILITY_CONCEPTS = List.of(
            "DefinedBenefitPensionPlanProjectedBenefitObligation",
            "DefinedBenefitPensionPlanBenefitObligation"
    );
    private static final List<String> MINORITY_INTEREST_CONCEPTS = List.of(
            "MinorityInterest",
            "MinorityInterestInConsolidatedSubsidiaries",
            "NoncontrollingInterest"
    );
    private static final List<String> GOODWILL_CONCEPTS = List.of("Goodwill");
    private static final List<String> INTANGIBLE_CONCEPTS = List.of(
            "FiniteLivedIntangibleAssetsNet",
            "IndefiniteLivedIntangibleAssetsExcludingGoodwill",
            "IntangibleAssetsNetExcludingGoodwill"
    );
    private static final List<String> PRETAX_INCOME_CONCEPTS = List.of(
            "IncomeBeforeTaxExpenseBenefit",
            "PretaxIncome",
            "IncomeLossFromContinuingOperationsBeforeIncomeTaxesExtraordinaryItemsNoncontrollingInterest"
    );
    private static final List<String> INCOME_TAX_CONCEPTS = List.of(
            "IncomeTaxExpenseBenefit",
            "IncomeTaxes"
    );
    private static final int MIN_DIRECT_QUARTER_DAYS = 45;
    private static final int MAX_DIRECT_QUARTER_DAYS = 120;

    private final UsSecurityMasterService usSecurityMasterService;
    private final SourceDocumentFactRawRepository sourceDocumentFactRawRepository;
    private final FinancialStandardizedRepository financialStandardizedRepository;
    private final UsFinancialDerivedMetricsService usFinancialDerivedMetricsService;
    private final ObjectMapper objectMapper;

    public UsFinancialStandardizationService(
            UsSecurityMasterService usSecurityMasterService,
            SourceDocumentFactRawRepository sourceDocumentFactRawRepository,
            FinancialStandardizedRepository financialStandardizedRepository,
            UsFinancialDerivedMetricsService usFinancialDerivedMetricsService,
            ObjectMapper objectMapper
    ) {
        this.usSecurityMasterService = usSecurityMasterService;
        this.sourceDocumentFactRawRepository = sourceDocumentFactRawRepository;
        this.financialStandardizedRepository = financialStandardizedRepository;
        this.usFinancialDerivedMetricsService = usFinancialDerivedMetricsService;
        this.objectMapper = objectMapper;
    }

    public StandardizationSummary standardizeTicker(String rawTicker) {
        String ticker = normalizeTicker(rawTicker);
        long securityId = usSecurityMasterService.resolveSecurityId(ticker)
                .orElseThrow(() -> new IllegalStateException("Ticker " + ticker + " does not exist in security master."));

        List<UsRawFactEntry> rawFacts = sourceDocumentFactRawRepository.findBySecurityId(securityId);
        if (rawFacts.isEmpty()) {
            throw new IllegalStateException("No raw SEC facts found for ticker " + ticker + ".");
        }

        List<UsFinancialStandardizedRecord> rows = buildStandardizedRows(securityId, rawFacts);
        financialStandardizedRepository.replaceBySecurityId(securityId, rows);
        int derivedMetricRowCount = usFinancialDerivedMetricsService.refreshForSecurity(securityId, rows);

        long fyCount = rows.stream().filter(row -> "FY".equals(row.periodType())).count();
        long quarterCount = rows.stream().filter(row -> "Q".equals(row.periodType())).count();
        long ttmCount = rows.stream().filter(row -> "TTM".equals(row.periodType())).count();

        return new StandardizationSummary(
                ticker,
                securityId,
                rawFacts.size(),
                rows.size(),
                derivedMetricRowCount,
                fyCount,
                quarterCount,
                ttmCount,
                "completed"
        );
    }

    private List<UsFinancialStandardizedRecord> buildStandardizedRows(long securityId, List<UsRawFactEntry> rawFacts) {
        Integer fiscalYearEndMonth = inferFiscalYearEndMonth(rawFacts);
        Map<SourcePeriodKey, List<UsRawFactEntry>> grouped = rawFacts.stream()
                .filter(fact -> fact.periodEnd() != null)
                .map(fact -> new SourceFactWrapper(fact, inferPeriodType(fact)))
                .filter(wrapper -> wrapper.periodType() != null)
                .collect(Collectors.groupingBy(
                        wrapper -> new SourcePeriodKey(
                                wrapper.fact().sourceDocumentId(),
                                wrapper.periodType(),
                                normalizeFiscalYear(wrapper.fact(), wrapper.periodType(), fiscalYearEndMonth),
                                normalizeFiscalPeriod(wrapper.fact(), wrapper.periodType()),
                                wrapper.fact().periodEnd()
                        ),
                        LinkedHashMap::new,
                        Collectors.mapping(SourceFactWrapper::fact, Collectors.toList())
                ));

        Map<CanonicalPeriodKey, CandidateRow> deduped = new LinkedHashMap<>();
        for (Map.Entry<SourcePeriodKey, List<UsRawFactEntry>> entry : grouped.entrySet()) {
            UsFinancialStandardizedRecord record = toPreliminaryRecord(securityId, entry.getKey(), entry.getValue());
            if (!hasUsefulValues(record)) {
                continue;
            }
            CandidateRow candidate = new CandidateRow(entry.getKey(), record, entry.getValue());
            CanonicalPeriodKey periodKey = new CanonicalPeriodKey(
                    record.periodType(),
                    record.fiscalYear(),
                    record.fiscalPeriod(),
                    record.periodEnd()
            );
            deduped.merge(periodKey, candidate, this::preferCandidate);
        }

        List<CandidateRow> selectedCandidates = deduped.values().stream()
                .sorted(Comparator
                        .comparing((CandidateRow row) -> row.record().periodEnd())
                        .thenComparing(row -> row.record().periodType()))
                .toList();
        List<UsFinancialStandardizedRecord> primaryRows = normalizeCandidateRows(securityId, selectedCandidates);
        List<UsFinancialStandardizedRecord> derivedFourthQuarterRows = buildDerivedFourthQuarterRows(securityId, primaryRows);

        List<UsFinancialStandardizedRecord> rowsBeforeTtm = new ArrayList<>(primaryRows);
        rowsBeforeTtm.addAll(derivedFourthQuarterRows);
        rowsBeforeTtm.sort(Comparator
                .comparing(UsFinancialStandardizedRecord::periodEnd)
                .thenComparing(UsFinancialStandardizedRecord::periodType)
                .thenComparing(row -> quarterIndex(row.fiscalPeriod())));

        List<UsFinancialStandardizedRecord> quarterRows = rowsBeforeTtm.stream()
                .filter(row -> "Q".equals(row.periodType()))
                .sorted(Comparator.comparingInt(this::periodSequence))
                .toList();
        List<UsFinancialStandardizedRecord> ttmRows = buildTtmRows(securityId, quarterRows);

        List<UsFinancialStandardizedRecord> allRows = new ArrayList<>(rowsBeforeTtm);
        allRows.addAll(ttmRows);
        allRows.sort(Comparator
                .comparing(UsFinancialStandardizedRecord::periodEnd)
                .thenComparing(UsFinancialStandardizedRecord::periodType)
                .thenComparing(row -> quarterIndex(row.fiscalPeriod())));
        return allRows;
    }

    private Integer inferFiscalYearEndMonth(List<UsRawFactEntry> rawFacts) {
        return rawFacts.stream()
                .filter(fact -> fact.periodEnd() != null)
                .filter(fact -> "FY".equals(inferPeriodType(fact)))
                .collect(Collectors.groupingBy(fact -> fact.periodEnd().getMonthValue(), Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.<Integer, Long>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private Integer normalizeFiscalYear(UsRawFactEntry fact, String periodType, Integer fiscalYearEndMonth) {
        if (fact.periodEnd() == null) {
            return fact.fiscalYear();
        }
        if ("FY".equals(periodType)) {
            return fact.periodEnd().getYear();
        }
        if (!"Q".equals(periodType) || fiscalYearEndMonth == null) {
            return fact.fiscalYear();
        }
        return fact.periodEnd().getMonthValue() > fiscalYearEndMonth
                ? fact.periodEnd().getYear() + 1
                : fact.periodEnd().getYear();
    }

    private String normalizeFiscalPeriod(UsRawFactEntry fact, String periodType) {
        if ("FY".equals(periodType)) {
            return "FY";
        }
        return fact.fiscalPeriod();
    }

    private List<UsFinancialStandardizedRecord> normalizeCandidateRows(long securityId, List<CandidateRow> candidates) {
        Map<Integer, Map<Integer, UsFinancialStandardizedRecord>> priorQuarterRows = new HashMap<>();
        List<UsFinancialStandardizedRecord> normalized = new ArrayList<>();
        for (CandidateRow candidate : candidates) {
            UsFinancialStandardizedRecord row = toNormalizedRecord(securityId, candidate, priorQuarterRows);
            normalized.add(row);
            if ("Q".equals(row.periodType()) && row.fiscalYear() != null && quarterIndex(row.fiscalPeriod()) > 0) {
                priorQuarterRows.computeIfAbsent(row.fiscalYear(), ignored -> new HashMap<>())
                        .put(quarterIndex(row.fiscalPeriod()), row);
            }
        }
        return normalized;
    }

    private CandidateRow preferCandidate(CandidateRow left, CandidateRow right) {
        if (right.score() > left.score()) {
            return right;
        }
        if (right.score() == left.score() && right.latestFilingDate().isAfter(left.latestFilingDate())) {
            return right;
        }
        return left;
    }

    private UsFinancialStandardizedRecord toPreliminaryRecord(
            long securityId,
            SourcePeriodKey key,
            List<UsRawFactEntry> facts
    ) {
        BigDecimal revenue = selectPreliminaryFlowValue(key.periodType(), facts, REVENUE_CONCEPTS, "USD");
        BigDecimal grossProfit = selectPreliminaryFlowValue(key.periodType(), facts, GROSS_PROFIT_CONCEPTS, "USD");
        BigDecimal ebit = selectPreliminaryFlowValue(key.periodType(), facts, EBIT_CONCEPTS, "USD");
        BigDecimal depreciation = selectPreliminaryFlowValue(key.periodType(), facts, DEPRECIATION_CONCEPTS, "USD");
        BigDecimal ebitda = add(ebit, depreciation);
        BigDecimal netIncome = selectPreliminaryFlowValue(key.periodType(), facts, NET_INCOME_CONCEPTS, "USD");
        BigDecimal operatingCashFlow = selectPreliminaryFlowValue(key.periodType(), facts, OCF_CONCEPTS, "USD");
        BigDecimal capex = selectPreliminaryFlowValue(key.periodType(), facts, CAPEX_CONCEPTS, "USD");
        BigDecimal freeCashFlow = subtract(operatingCashFlow, capex);
        BigDecimal cash = firstNumeric(facts, CASH_CONCEPTS, "USD");
        BigDecimal shortTermDebt = firstNumeric(facts, SHORT_TERM_DEBT_CONCEPTS, "USD");
        BigDecimal longTermDebt = firstNumeric(facts, LONG_TERM_DEBT_CONCEPTS, "USD");
        BigDecimal totalDebt = sum(shortTermDebt, longTermDebt);
        BigDecimal equity = firstNumeric(facts, EQUITY_CONCEPTS, "USD");
        BigDecimal totalAssets = firstNumeric(facts, TOTAL_ASSETS_CONCEPTS, "USD");
        BigDecimal totalLiabilities = firstNumeric(facts, TOTAL_LIABILITIES_CONCEPTS, "USD");
        BigDecimal dilutedShares = firstNumeric(facts, DILUTED_SHARES_CONCEPTS, "shares");
        BigDecimal basicShares = firstNumeric(facts, BASIC_SHARES_CONCEPTS, "shares");
        BigDecimal sbc = selectPreliminaryFlowValue(key.periodType(), facts, SBC_CONCEPTS, "USD");
        BigDecimal leaseLiabilities = resolveLeaseLiabilities(facts);
        BigDecimal pensionLiabilities = firstNumeric(facts, PENSION_LIABILITY_CONCEPTS, "USD");
        BigDecimal minorityInterest = firstNumeric(facts, MINORITY_INTEREST_CONCEPTS, "USD");
        BigDecimal goodwill = firstNumeric(facts, GOODWILL_CONCEPTS, "USD");
        BigDecimal intangibles = firstNumeric(facts, INTANGIBLE_CONCEPTS, "USD");
        BigDecimal pretaxIncome = selectPreliminaryFlowValue(key.periodType(), facts, PRETAX_INCOME_CONCEPTS, "USD");
        BigDecimal incomeTax = selectPreliminaryFlowValue(key.periodType(), facts, INCOME_TAX_CONCEPTS, "USD");
        BigDecimal taxRate = ratio(incomeTax, pretaxIncome);
        BigDecimal netDebt = subtract(totalDebt, cash);

        Map<String, Object> qualityFlags = new LinkedHashMap<>();
        qualityFlags.put("document_type", facts.get(0).documentType());
        qualityFlags.put("missing_revenue", revenue == null);
        qualityFlags.put("missing_cash", cash == null);
        qualityFlags.put("missing_debt", totalDebt == null);
        qualityFlags.put("missing_shares", dilutedShares == null && basicShares == null);

        return new UsFinancialStandardizedRecord(
                securityId,
                key.periodType(),
                key.fiscalYear(),
                key.fiscalPeriod(),
                inferPeriodStart(facts),
                key.periodEnd(),
                revenue,
                grossProfit,
                ebitda,
                ebit,
                netIncome,
                operatingCashFlow,
                capex,
                freeCashFlow,
                cash,
                shortTermDebt,
                longTermDebt,
                totalDebt,
                equity,
                totalAssets,
                totalLiabilities,
                dilutedShares,
                basicShares,
                sbc,
                leaseLiabilities,
                pensionLiabilities,
                minorityInterest,
                goodwill,
                intangibles,
                taxRate,
                netDebt,
                key.sourceDocumentId(),
                toJson(qualityFlags)
        );
    }

    private UsFinancialStandardizedRecord toNormalizedRecord(
            long securityId,
            CandidateRow candidate,
            Map<Integer, Map<Integer, UsFinancialStandardizedRecord>> priorQuarterRows
    ) {
        UsFinancialStandardizedRecord base = candidate.record();
        String periodType = base.periodType();
        Integer fiscalYear = base.fiscalYear();
        int currentQuarterIndex = quarterIndex(base.fiscalPeriod());
        Map<Integer, UsFinancialStandardizedRecord> quarterHistory = fiscalYear == null
                ? Map.of()
                : priorQuarterRows.getOrDefault(fiscalYear, Map.of());

        BigDecimal revenue = resolveFlowMetric(candidate, REVENUE_CONCEPTS, "USD", quarterHistory, UsFinancialStandardizedRecord::revenue);
        BigDecimal grossProfit = resolveFlowMetric(candidate, GROSS_PROFIT_CONCEPTS, "USD", quarterHistory, UsFinancialStandardizedRecord::grossProfit);
        BigDecimal ebit = resolveFlowMetric(candidate, EBIT_CONCEPTS, "USD", quarterHistory, UsFinancialStandardizedRecord::ebit);
        BigDecimal depreciation = resolveFlowMetric(candidate, DEPRECIATION_CONCEPTS, "USD", quarterHistory, row -> subtract(row.ebitda(), row.ebit()));
        BigDecimal ebitda = add(ebit, depreciation);
        BigDecimal netIncome = resolveFlowMetric(candidate, NET_INCOME_CONCEPTS, "USD", quarterHistory, UsFinancialStandardizedRecord::netIncome);
        BigDecimal operatingCashFlow = resolveFlowMetric(candidate, OCF_CONCEPTS, "USD", quarterHistory, UsFinancialStandardizedRecord::operatingCashFlow);
        BigDecimal capex = resolveFlowMetric(candidate, CAPEX_CONCEPTS, "USD", quarterHistory, UsFinancialStandardizedRecord::capex);
        BigDecimal freeCashFlow = subtract(operatingCashFlow, capex);
        BigDecimal cash = firstNumeric(candidate.sourceFacts(), CASH_CONCEPTS, "USD");
        BigDecimal shortTermDebt = firstNumeric(candidate.sourceFacts(), SHORT_TERM_DEBT_CONCEPTS, "USD");
        BigDecimal longTermDebt = firstNumeric(candidate.sourceFacts(), LONG_TERM_DEBT_CONCEPTS, "USD");
        BigDecimal totalDebt = sum(shortTermDebt, longTermDebt);
        BigDecimal equity = firstNumeric(candidate.sourceFacts(), EQUITY_CONCEPTS, "USD");
        BigDecimal totalAssets = firstNumeric(candidate.sourceFacts(), TOTAL_ASSETS_CONCEPTS, "USD");
        BigDecimal totalLiabilities = firstNumeric(candidate.sourceFacts(), TOTAL_LIABILITIES_CONCEPTS, "USD");
        BigDecimal dilutedShares = firstNumeric(candidate.sourceFacts(), DILUTED_SHARES_CONCEPTS, "shares");
        BigDecimal basicShares = firstNumeric(candidate.sourceFacts(), BASIC_SHARES_CONCEPTS, "shares");
        BigDecimal sbc = resolveFlowMetric(candidate, SBC_CONCEPTS, "USD", quarterHistory, UsFinancialStandardizedRecord::sbc);
        BigDecimal leaseLiabilities = resolveLeaseLiabilities(candidate.sourceFacts());
        BigDecimal pensionLiabilities = firstNumeric(candidate.sourceFacts(), PENSION_LIABILITY_CONCEPTS, "USD");
        BigDecimal minorityInterest = firstNumeric(candidate.sourceFacts(), MINORITY_INTEREST_CONCEPTS, "USD");
        BigDecimal goodwill = firstNumeric(candidate.sourceFacts(), GOODWILL_CONCEPTS, "USD");
        BigDecimal intangibles = firstNumeric(candidate.sourceFacts(), INTANGIBLE_CONCEPTS, "USD");
        BigDecimal pretaxIncome = resolveFlowMetric(candidate, PRETAX_INCOME_CONCEPTS, "USD", quarterHistory, row -> null);
        BigDecimal incomeTax = resolveFlowMetric(candidate, INCOME_TAX_CONCEPTS, "USD", quarterHistory, row -> null);
        BigDecimal taxRate = ratio(incomeTax, pretaxIncome);
        BigDecimal netDebt = subtract(totalDebt, cash);
        LocalDate periodStart = resolvePeriodStart(candidate, quarterHistory, currentQuarterIndex, periodType);

        Map<String, Object> qualityFlags = new LinkedHashMap<>();
        qualityFlags.put("document_type", candidate.sourceFacts().get(0).documentType());
        qualityFlags.put("missing_revenue", revenue == null);
        qualityFlags.put("missing_cash", cash == null);
        qualityFlags.put("missing_debt", totalDebt == null);
        qualityFlags.put("missing_shares", dilutedShares == null && basicShares == null);
        if ("Q".equals(periodType)) {
            qualityFlags.put("quarter_index", currentQuarterIndex);
        }

        return new UsFinancialStandardizedRecord(
                securityId,
                periodType,
                fiscalYear,
                base.fiscalPeriod(),
                periodStart,
                base.periodEnd(),
                revenue,
                grossProfit,
                ebitda,
                ebit,
                netIncome,
                operatingCashFlow,
                capex,
                freeCashFlow,
                cash,
                shortTermDebt,
                longTermDebt,
                totalDebt,
                equity,
                totalAssets,
                totalLiabilities,
                dilutedShares,
                basicShares,
                sbc,
                leaseLiabilities,
                pensionLiabilities,
                minorityInterest,
                goodwill,
                intangibles,
                taxRate,
                netDebt,
                base.sourceDocumentId(),
                toJson(qualityFlags)
        );
    }

    private List<UsFinancialStandardizedRecord> buildDerivedFourthQuarterRows(long securityId, List<UsFinancialStandardizedRecord> rows) {
        Map<Integer, List<UsFinancialStandardizedRecord>> quarterRowsByYear = rows.stream()
                .filter(row -> "Q".equals(row.periodType()) && row.fiscalYear() != null)
                .collect(Collectors.groupingBy(UsFinancialStandardizedRecord::fiscalYear));

        List<UsFinancialStandardizedRecord> derivedRows = new ArrayList<>();
        for (UsFinancialStandardizedRecord fyRow : rows.stream().filter(row -> "FY".equals(row.periodType())).toList()) {
            if (fyRow.fiscalYear() == null) {
                continue;
            }
            List<UsFinancialStandardizedRecord> quarterRows = quarterRowsByYear.getOrDefault(fyRow.fiscalYear(), List.of());
            if (quarterRows.stream().anyMatch(row -> quarterIndex(row.fiscalPeriod()) == 4)) {
                continue;
            }
            Map<Integer, UsFinancialStandardizedRecord> quarterMap = quarterRows.stream()
                    .filter(this::hasTtmSignal)
                    .filter(row -> quarterIndex(row.fiscalPeriod()) > 0)
                    .collect(Collectors.toMap(
                            row -> quarterIndex(row.fiscalPeriod()),
                            Function.identity(),
                            (left, right) -> right
                    ));
            if (!quarterMap.keySet().containsAll(List.of(1, 2, 3))) {
                continue;
            }

            UsFinancialStandardizedRecord q1 = quarterMap.get(1);
            UsFinancialStandardizedRecord q2 = quarterMap.get(2);
            UsFinancialStandardizedRecord q3 = quarterMap.get(3);
            List<UsFinancialStandardizedRecord> q1ToQ3 = List.of(q1, q2, q3);

            Map<String, Object> qualityFlags = new LinkedHashMap<>();
            qualityFlags.put("derived_q4_from_fy", true);
            qualityFlags.put("source_document_type", "10-K");

            derivedRows.add(new UsFinancialStandardizedRecord(
                    securityId,
                    "Q",
                    fyRow.fiscalYear(),
                    "Q4",
                    q3.periodEnd() == null ? fyRow.periodStart() : q3.periodEnd().plusDays(1),
                    fyRow.periodEnd(),
                    subtract(fyRow.revenue(), sum(q1ToQ3, UsFinancialStandardizedRecord::revenue)),
                    subtract(fyRow.grossProfit(), sum(q1ToQ3, UsFinancialStandardizedRecord::grossProfit)),
                    subtract(fyRow.ebitda(), sum(q1ToQ3, UsFinancialStandardizedRecord::ebitda)),
                    subtract(fyRow.ebit(), sum(q1ToQ3, UsFinancialStandardizedRecord::ebit)),
                    subtract(fyRow.netIncome(), sum(q1ToQ3, UsFinancialStandardizedRecord::netIncome)),
                    subtract(fyRow.operatingCashFlow(), sum(q1ToQ3, UsFinancialStandardizedRecord::operatingCashFlow)),
                    subtract(fyRow.capex(), sum(q1ToQ3, UsFinancialStandardizedRecord::capex)),
                    subtract(fyRow.freeCashFlow(), sum(q1ToQ3, UsFinancialStandardizedRecord::freeCashFlow)),
                    fyRow.cash(),
                    fyRow.shortTermDebt(),
                    fyRow.longTermDebt(),
                    fyRow.totalDebt(),
                    fyRow.equity(),
                    fyRow.totalAssets(),
                    fyRow.totalLiabilities(),
                    fyRow.dilutedShares(),
                    fyRow.basicShares(),
                    subtract(fyRow.sbc(), sum(q1ToQ3, UsFinancialStandardizedRecord::sbc)),
                    fyRow.leaseLiabilities(),
                    fyRow.pensionLiabilities(),
                    fyRow.minorityInterest(),
                    fyRow.goodwill(),
                    fyRow.intangibles(),
                    fyRow.taxRateEffective(),
                    fyRow.netDebt(),
                    fyRow.sourceDocumentId(),
                    toJson(qualityFlags)
            ));
        }
        return derivedRows;
    }

    private List<UsFinancialStandardizedRecord> buildTtmRows(long securityId, List<UsFinancialStandardizedRecord> quarterRows) {
        List<UsFinancialStandardizedRecord> flowQuarterRows = quarterRows.stream()
                .filter(this::hasTtmSignal)
                .sorted(Comparator.comparingInt(this::periodSequence))
                .toList();
        List<UsFinancialStandardizedRecord> ttmRows = new ArrayList<>();
        for (int index = 3; index < flowQuarterRows.size(); index += 1) {
            List<UsFinancialStandardizedRecord> window = flowQuarterRows.subList(index - 3, index + 1);
            if (!isConsecutiveQuarterWindow(window)) {
                continue;
            }
            LocalDate oldestEnd = window.get(0).periodEnd();
            LocalDate latestEnd = window.get(3).periodEnd();
            if (ChronoUnit.DAYS.between(oldestEnd, latestEnd) > 380) {
                continue;
            }

            UsFinancialStandardizedRecord latest = window.get(3);
            Map<String, Object> qualityFlags = new LinkedHashMap<>();
            qualityFlags.put("derived_from", "ttm");
            qualityFlags.put("quarter_count", 4);

            ttmRows.add(new UsFinancialStandardizedRecord(
                    securityId,
                    "TTM",
                    latest.fiscalYear(),
                    latest.fiscalPeriod(),
                    window.get(0).periodStart(),
                    latest.periodEnd(),
                    sum(window, UsFinancialStandardizedRecord::revenue),
                    sum(window, UsFinancialStandardizedRecord::grossProfit),
                    sum(window, UsFinancialStandardizedRecord::ebitda),
                    sum(window, UsFinancialStandardizedRecord::ebit),
                    sum(window, UsFinancialStandardizedRecord::netIncome),
                    sum(window, UsFinancialStandardizedRecord::operatingCashFlow),
                    sum(window, UsFinancialStandardizedRecord::capex),
                    sum(window, UsFinancialStandardizedRecord::freeCashFlow),
                    latest.cash(),
                    latest.shortTermDebt(),
                    latest.longTermDebt(),
                    latest.totalDebt(),
                    latest.equity(),
                    latest.totalAssets(),
                    latest.totalLiabilities(),
                    latest.dilutedShares(),
                    latest.basicShares(),
                    sum(window, UsFinancialStandardizedRecord::sbc),
                    latest.leaseLiabilities(),
                    latest.pensionLiabilities(),
                    latest.minorityInterest(),
                    latest.goodwill(),
                    latest.intangibles(),
                    latest.taxRateEffective(),
                    latest.netDebt(),
                    latest.sourceDocumentId(),
                    toJson(qualityFlags)
            ));
        }
        return ttmRows;
    }

    private boolean isConsecutiveQuarterWindow(List<UsFinancialStandardizedRecord> window) {
        for (int index = 1; index < window.size(); index += 1) {
            if (periodSequence(window.get(index)) - periodSequence(window.get(index - 1)) != 1) {
                return false;
            }
        }
        return true;
    }

    private int periodSequence(UsFinancialStandardizedRecord row) {
        if (row.fiscalYear() == null) {
            return Integer.MIN_VALUE;
        }
        return row.fiscalYear() * 4 + Math.max(quarterIndex(row.fiscalPeriod()), 0);
    }

    private BigDecimal resolveFlowMetric(
            CandidateRow candidate,
            List<String> concepts,
            String unit,
            Map<Integer, UsFinancialStandardizedRecord> priorQuarterRows,
            Function<UsFinancialStandardizedRecord, BigDecimal> priorExtractor
    ) {
        String periodType = candidate.record().periodType();
        if ("FY".equals(periodType)) {
            FactSelection annual = selectAnnualFlowFact(candidate.sourceFacts(), concepts, unit);
            return annual == null ? null : annual.value();
        }
        if (!"Q".equals(periodType)) {
            FactSelection generic = selectAnnualFlowFact(candidate.sourceFacts(), concepts, unit);
            return generic == null ? null : generic.value();
        }

        FactSelection directQuarter = selectDirectQuarterFact(candidate.sourceFacts(), concepts, unit);
        if (directQuarter != null) {
            return directQuarter.value();
        }

        FactSelection cumulative = selectCumulativeQuarterFact(candidate.sourceFacts(), concepts, unit);
        if (cumulative == null) {
            return null;
        }

        int quarterIndex = quarterIndex(candidate.record().fiscalPeriod());
        if (quarterIndex <= 1) {
            return cumulative.value();
        }

        BigDecimal priorSum = BigDecimal.ZERO;
        for (int previousQuarter = 1; previousQuarter < quarterIndex; previousQuarter += 1) {
            UsFinancialStandardizedRecord previousRow = priorQuarterRows.get(previousQuarter);
            if (previousRow == null) {
                return null;
            }
            BigDecimal previousValue = priorExtractor.apply(previousRow);
            if (previousValue == null) {
                return null;
            }
            priorSum = priorSum.add(previousValue);
        }
        return cumulative.value().subtract(priorSum);
    }

    private BigDecimal selectPreliminaryFlowValue(String periodType, List<UsRawFactEntry> facts, List<String> concepts, String unit) {
        if ("Q".equals(periodType)) {
            FactSelection direct = selectDirectQuarterFact(facts, concepts, unit);
            if (direct != null) {
                return direct.value();
            }
        }
        FactSelection annual = selectAnnualFlowFact(facts, concepts, unit);
        return annual == null ? null : annual.value();
    }

    private FactSelection selectDirectQuarterFact(List<UsRawFactEntry> facts, List<String> concepts, String unit) {
        return selectFacts(facts, concepts, unit).stream()
                .filter(selection -> selection.durationDays() >= MIN_DIRECT_QUARTER_DAYS)
                .filter(selection -> selection.durationDays() <= MAX_DIRECT_QUARTER_DAYS)
                .min(Comparator
                        .comparingLong(FactSelection::durationDays)
                        .thenComparing(FactSelection::periodStart, Comparator.nullsLast(Comparator.reverseOrder())))
                .orElse(null);
    }

    private FactSelection selectCumulativeQuarterFact(List<UsRawFactEntry> facts, List<String> concepts, String unit) {
        return selectFacts(facts, concepts, unit).stream()
                .max(Comparator
                        .comparingLong(FactSelection::durationDays)
                        .thenComparing(FactSelection::periodStart, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private FactSelection selectAnnualFlowFact(List<UsRawFactEntry> facts, List<String> concepts, String unit) {
        return selectFacts(facts, concepts, unit).stream()
                .max(Comparator
                        .comparingLong(FactSelection::durationDays)
                        .thenComparing(FactSelection::periodStart, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private List<FactSelection> selectFacts(List<UsRawFactEntry> facts, List<String> concepts, String unit) {
        return facts.stream()
                .filter(fact -> concepts.contains(fact.conceptName()))
                .filter(fact -> unit == null || unit.equalsIgnoreCase(fact.unit()))
                .filter(fact -> fact.valueNumeric() != null)
                .map(this::toFactSelection)
                .toList();
    }

    private FactSelection toFactSelection(UsRawFactEntry fact) {
        long durationDays = fact.periodStart() == null || fact.periodEnd() == null
                ? Long.MIN_VALUE
                : ChronoUnit.DAYS.between(fact.periodStart(), fact.periodEnd());
        return new FactSelection(fact.valueNumeric(), fact.periodStart(), fact.periodEnd(), durationDays);
    }

    private BigDecimal resolveLeaseLiabilities(List<UsRawFactEntry> facts) {
        BigDecimal aggregate = firstNumeric(facts, List.of("OperatingLeaseLiability", "FinanceLeaseLiability"), "USD");
        if (aggregate != null) {
            return aggregate;
        }
        BigDecimal operating = sumAllNumeric(facts, List.of("OperatingLeaseLiabilityCurrent", "OperatingLeaseLiabilityNoncurrent"), "USD");
        BigDecimal finance = sumAllNumeric(facts, List.of("FinanceLeaseLiabilityCurrent", "FinanceLeaseLiabilityNoncurrent"), "USD");
        return add(operating, finance);
    }

    private LocalDate resolvePeriodStart(
            CandidateRow candidate,
            Map<Integer, UsFinancialStandardizedRecord> priorQuarterRows,
            int currentQuarterIndex,
            String periodType
    ) {
        if (!"Q".equals(periodType)) {
            return inferPeriodStart(candidate.sourceFacts());
        }
        FactSelection directQuarter = selectDirectQuarterFact(candidate.sourceFacts(), REVENUE_CONCEPTS, "USD");
        if (directQuarter == null) {
            directQuarter = selectDirectQuarterFact(candidate.sourceFacts(), NET_INCOME_CONCEPTS, "USD");
        }
        if (directQuarter == null) {
            directQuarter = selectDirectQuarterFact(candidate.sourceFacts(), OCF_CONCEPTS, "USD");
        }
        if (directQuarter != null && directQuarter.periodStart() != null) {
            return directQuarter.periodStart();
        }
        if (currentQuarterIndex > 1) {
            UsFinancialStandardizedRecord previousQuarter = priorQuarterRows.get(currentQuarterIndex - 1);
            if (previousQuarter != null && previousQuarter.periodEnd() != null) {
                return previousQuarter.periodEnd().plusDays(1);
            }
        }
        return inferPeriodStart(candidate.sourceFacts());
    }

    private boolean hasTtmSignal(UsFinancialStandardizedRecord record) {
        return record.revenue() != null
                || record.netIncome() != null
                || record.operatingCashFlow() != null
                || record.capex() != null
                || record.ebit() != null
                || record.ebitda() != null;
    }

    private boolean hasUsefulValues(UsFinancialStandardizedRecord record) {
        return record.revenue() != null
                || record.netIncome() != null
                || record.operatingCashFlow() != null
                || record.cash() != null
                || record.totalDebt() != null
                || record.equity() != null
                || record.totalAssets() != null
                || record.totalLiabilities() != null;
    }

    private String inferPeriodType(UsRawFactEntry fact) {
        String documentType = fact.documentType() == null ? "" : fact.documentType().toUpperCase(Locale.ROOT);
        String fiscalPeriod = fact.fiscalPeriod() == null ? "" : fact.fiscalPeriod().toUpperCase(Locale.ROOT);
        long durationDays = fact.periodStart() == null || fact.periodEnd() == null
                ? -1
                : ChronoUnit.DAYS.between(fact.periodStart(), fact.periodEnd());

        if (documentType.startsWith("10-K") || "20-F".equals(documentType) || "FY".equals(fiscalPeriod)) {
            return "FY";
        }
        if (documentType.startsWith("10-Q") || fiscalPeriod.matches("Q[1-4]")) {
            return "Q";
        }
        if (durationDays >= 300) {
            return "FY";
        }
        if (durationDays >= 70 && durationDays <= 120) {
            return "Q";
        }
        return null;
    }

    private int quarterIndex(String fiscalPeriod) {
        if (fiscalPeriod == null) {
            return -1;
        }
        return switch (fiscalPeriod.toUpperCase(Locale.ROOT)) {
            case "Q1" -> 1;
            case "Q2" -> 2;
            case "Q3" -> 3;
            case "Q4" -> 4;
            default -> -1;
        };
    }

    private LocalDate inferPeriodStart(List<UsRawFactEntry> facts) {
        return facts.stream()
                .map(UsRawFactEntry::periodStart)
                .filter(value -> value != null)
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    private BigDecimal firstNumeric(List<UsRawFactEntry> facts, List<String> concepts, String unit) {
        for (String concept : concepts) {
            Optional<BigDecimal> match = facts.stream()
                    .filter(fact -> concept.equals(fact.conceptName()))
                    .filter(fact -> unit == null || unit.equalsIgnoreCase(fact.unit()))
                    .map(UsRawFactEntry::valueNumeric)
                    .filter(value -> value != null)
                    .findFirst();
            if (match.isPresent()) {
                return match.get();
            }
        }
        return null;
    }

    private BigDecimal sumAllNumeric(List<UsRawFactEntry> facts, List<String> concepts, String unit) {
        BigDecimal total = null;
        for (String concept : concepts) {
            for (UsRawFactEntry fact : facts) {
                if (concept.equals(fact.conceptName())
                        && (unit == null || unit.equalsIgnoreCase(fact.unit()))
                        && fact.valueNumeric() != null) {
                    total = total == null ? fact.valueNumeric() : total.add(fact.valueNumeric());
                }
            }
        }
        return total;
    }

    private BigDecimal add(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return null;
        }
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.add(right);
    }

    private BigDecimal subtract(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return null;
        }
        return left.subtract(right);
    }

    private BigDecimal sum(BigDecimal left, BigDecimal right) {
        return add(left, right);
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(List<UsFinancialStandardizedRecord> rows, java.util.function.Function<UsFinancialStandardizedRecord, BigDecimal> extractor) {
        BigDecimal total = null;
        for (UsFinancialStandardizedRecord row : rows) {
            BigDecimal value = extractor.apply(row);
            if (value != null) {
                total = total == null ? value : total.add(value);
            }
        }
        return total;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize financial_standardized quality flags.", ex);
        }
    }

    private String normalizeTicker(String rawTicker) {
        return rawTicker.trim()
                .toUpperCase(Locale.ROOT)
                .replace(".US", "")
                .replace(".", "-");
    }

    private record SourceFactWrapper(UsRawFactEntry fact, String periodType) {
    }

    private record SourcePeriodKey(
            long sourceDocumentId,
            String periodType,
            Integer fiscalYear,
            String fiscalPeriod,
            LocalDate periodEnd
    ) {
    }

    private record CanonicalPeriodKey(
            String periodType,
            Integer fiscalYear,
            String fiscalPeriod,
            LocalDate periodEnd
    ) {
    }

    private record FactSelection(
            BigDecimal value,
            LocalDate periodStart,
            LocalDate periodEnd,
            long durationDays
    ) {
    }

    private record CandidateRow(SourcePeriodKey key, UsFinancialStandardizedRecord record, List<UsRawFactEntry> sourceFacts) {
        private int score() {
            int populated = 0;
            populated += record.revenue() != null ? 2 : 0;
            populated += record.netIncome() != null ? 2 : 0;
            populated += record.operatingCashFlow() != null ? 2 : 0;
            populated += record.cash() != null ? 1 : 0;
            populated += record.totalDebt() != null ? 1 : 0;
            populated += record.equity() != null ? 1 : 0;
            populated += record.dilutedShares() != null || record.basicShares() != null ? 1 : 0;
            populated += documentPriority();
            return populated;
        }

        private LocalDate latestFilingDate() {
            return sourceFacts.stream()
                    .map(UsRawFactEntry::filingDate)
                    .filter(value -> value != null)
                    .max(LocalDate::compareTo)
                    .orElse(LocalDate.MIN);
        }

        private int documentPriority() {
            String documentType = sourceFacts.get(0).documentType();
            if (documentType == null) {
                return 0;
            }
            String normalized = documentType.toUpperCase(Locale.ROOT);
            if (normalized.startsWith("10-K") || "20-F".equals(normalized)) {
                return 4;
            }
            if (normalized.startsWith("10-Q")) {
                return 3;
            }
            if (normalized.startsWith("8-K")) {
                return 2;
            }
            return 1;
        }
    }

    public record StandardizationSummary(
            String ticker,
            long securityId,
            int rawFactCount,
            int standardizedRowCount,
            int derivedMetricRowCount,
            long fyCount,
            long quarterCount,
            long ttmCount,
            String status
    ) {
    }
}
