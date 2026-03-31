package com.fairvalue.engine.us;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "market-data.us.live.enabled=true",
        "market-data.us.live.universe-sync-on-startup=false"
})
class UsFinancialStandardizationServiceTest {
    @Autowired
    private UsFinancialStandardizationService usFinancialStandardizationService;

    @Autowired
    private UsSecurityMasterService usSecurityMasterService;

    @Autowired
    private JdbcClient jdbcClient;

    private long aaplSecurityId;
    private long secEdgarSourceId;

    @BeforeEach
    void setUp() {
        aaplSecurityId = usSecurityMasterService.resolveSecurityId("AAPL").orElseThrow();
        secEdgarSourceId = jdbcClient.sql("""
                        SELECT id
                        FROM fairvalue.source_registry
                        WHERE source_name = 'sec_edgar'
                        """)
                .query(Long.class)
                .single();

        jdbcClient.sql("DELETE FROM fairvalue.financial_standardized WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.financial_derived_metrics WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.source_document_facts_raw WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("""
                        DELETE FROM fairvalue.source_documents
                        WHERE security_id = :securityId
                          AND source_id = :sourceId
                        """)
                .param("securityId", aaplSecurityId)
                .param("sourceId", secEdgarSourceId)
                .update();
    }

    @Test
    void shouldStandardizeQuarterFactsIntoQuarterAndTtmRows() {
        insertQuarter("0000320193-25-000010", LocalDate.of(2025, 2, 1), LocalDate.of(2024, 12, 28), "Q1", 100, 20, 24, 6, 55, 5, 40, 20, 70, 30, 10);
        insertQuarter("0000320193-25-000020", LocalDate.of(2025, 5, 2), LocalDate.of(2025, 3, 29), "Q2", 110, 22, 26, 7, 56, 6, 41, 21, 71, 31, 10);
        insertQuarter("0000320193-25-000030", LocalDate.of(2025, 8, 1), LocalDate.of(2025, 6, 28), "Q3", 120, 24, 28, 8, 57, 7, 42, 22, 72, 32, 10);
        insertQuarter("0000320193-25-000040", LocalDate.of(2025, 11, 1), LocalDate.of(2025, 9, 27), "Q4", 130, 26, 30, 9, 58, 8, 43, 23, 73, 33, 10);

        UsFinancialStandardizationService.StandardizationSummary summary =
                usFinancialStandardizationService.standardizeTicker("AAPL");

        assertThat(summary.ticker()).isEqualTo("AAPL");
        assertThat(summary.securityId()).isEqualTo(aaplSecurityId);
        assertThat(summary.rawFactCount()).isEqualTo(44);
        assertThat(summary.standardizedRowCount()).isEqualTo(5);
        assertThat(summary.derivedMetricRowCount()).isEqualTo(5);
        assertThat(summary.fyCount()).isZero();
        assertThat(summary.quarterCount()).isEqualTo(4);
        assertThat(summary.ttmCount()).isEqualTo(1);
        assertThat(summary.status()).isEqualTo("completed");

        Long storedCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.financial_standardized
                        WHERE security_id = :securityId
                        """)
                .param("securityId", aaplSecurityId)
                .query(Long.class)
                .single();
        BigDecimal ttmRevenue = jdbcClient.sql("""
                        SELECT revenue
                        FROM fairvalue.financial_standardized
                        WHERE security_id = :securityId
                          AND period_type = 'TTM'
                          AND period_end = DATE '2025-09-27'
                        """)
                .param("securityId", aaplSecurityId)
                .query(BigDecimal.class)
                .single();
        BigDecimal ttmFreeCashFlow = jdbcClient.sql("""
                        SELECT free_cash_flow
                        FROM fairvalue.financial_standardized
                        WHERE security_id = :securityId
                          AND period_type = 'TTM'
                          AND period_end = DATE '2025-09-27'
                        """)
                .param("securityId", aaplSecurityId)
                .query(BigDecimal.class)
                .single();
        String latestQualityJson = jdbcClient.sql("""
                        SELECT quality_flag_json::text
                        FROM fairvalue.financial_standardized
                        WHERE security_id = :securityId
                          AND period_type = 'Q'
                          AND period_end = DATE '2025-09-27'
                        """)
                .param("securityId", aaplSecurityId)
                .query(String.class)
                .single();
        BigDecimal derivedRoiC = jdbcClient.sql("""
                        SELECT roic
                        FROM fairvalue.financial_derived_metrics
                        WHERE security_id = :securityId
                          AND period_type = 'TTM'
                          AND period_end = DATE '2025-09-27'
                        """)
                .param("securityId", aaplSecurityId)
                .query(BigDecimal.class)
                .single();
        BigDecimal derivedBookValuePerShare = jdbcClient.sql("""
                        SELECT book_value_per_share
                        FROM fairvalue.financial_derived_metrics
                        WHERE security_id = :securityId
                          AND period_type = 'Q'
                          AND period_end = DATE '2025-09-27'
                        """)
                .param("securityId", aaplSecurityId)
                .query(BigDecimal.class)
                .single();

        assertThat(storedCount).isEqualTo(5);
        assertThat(ttmRevenue).isEqualByComparingTo("460");
        assertThat(ttmFreeCashFlow).isEqualByComparingTo("78");
        assertThat(latestQualityJson).contains("\"document_type\": \"10-Q\"");
        assertThat(derivedRoiC).isNotNull();
        assertThat(derivedBookValuePerShare).isEqualByComparingTo("2.300000");
    }

    @Test
    void shouldNormalizeYtdQuarterFactsAndDeriveFourthQuarter() {
        LocalDate fyStart = LocalDate.of(2024, 9, 29);
        insertQuarter("0000320193-25-000010", LocalDate.of(2025, 2, 1), LocalDate.of(2024, 12, 28), "Q1", 100, 20, 24, 6, 55, 5, 40, 20, 70, 30, 10);

        Long q2Doc = insertDocument("0000320193-25-000020", LocalDate.of(2025, 5, 2), LocalDate.of(2025, 3, 29), "10-Q");
        insertNumericFact(q2Doc, "RevenueFromContractWithCustomerExcludingAssessedTax", "USD", LocalDate.of(2025, 5, 2), fyStart, LocalDate.of(2025, 3, 29), "Q2", 210);
        insertNumericFact(q2Doc, "NetIncomeLoss", "USD", LocalDate.of(2025, 5, 2), fyStart, LocalDate.of(2025, 3, 29), "Q2", 42);
        insertNumericFact(q2Doc, "NetCashProvidedByUsedInOperatingActivities", "USD", LocalDate.of(2025, 5, 2), fyStart, LocalDate.of(2025, 3, 29), "Q2", 50);
        insertNumericFact(q2Doc, "PaymentsToAcquirePropertyPlantAndEquipment", "USD", LocalDate.of(2025, 5, 2), fyStart, LocalDate.of(2025, 3, 29), "Q2", 13);
        insertNumericFact(q2Doc, "CashAndCashEquivalentsAtCarryingValue", "USD", LocalDate.of(2025, 5, 2), null, LocalDate.of(2025, 3, 29), "Q2", 56);
        insertNumericFact(q2Doc, "LongTermDebtCurrent", "USD", LocalDate.of(2025, 5, 2), null, LocalDate.of(2025, 3, 29), "Q2", 6);
        insertNumericFact(q2Doc, "LongTermDebtNoncurrent", "USD", LocalDate.of(2025, 5, 2), null, LocalDate.of(2025, 3, 29), "Q2", 41);
        insertNumericFact(q2Doc, "StockholdersEquity", "USD", LocalDate.of(2025, 5, 2), null, LocalDate.of(2025, 3, 29), "Q2", 21);
        insertNumericFact(q2Doc, "Assets", "USD", LocalDate.of(2025, 5, 2), null, LocalDate.of(2025, 3, 29), "Q2", 71);
        insertNumericFact(q2Doc, "Liabilities", "USD", LocalDate.of(2025, 5, 2), null, LocalDate.of(2025, 3, 29), "Q2", 31);
        insertNumericFact(q2Doc, "EntityCommonStockSharesOutstanding", "shares", LocalDate.of(2025, 5, 2), null, LocalDate.of(2025, 3, 29), "Q2", 10);

        Long q3Doc = insertDocument("0000320193-25-000030", LocalDate.of(2025, 8, 1), LocalDate.of(2025, 6, 28), "10-Q");
        insertNumericFact(q3Doc, "RevenueFromContractWithCustomerExcludingAssessedTax", "USD", LocalDate.of(2025, 8, 1), fyStart, LocalDate.of(2025, 6, 28), "Q3", 330);
        insertNumericFact(q3Doc, "NetIncomeLoss", "USD", LocalDate.of(2025, 8, 1), fyStart, LocalDate.of(2025, 6, 28), "Q3", 66);
        insertNumericFact(q3Doc, "NetCashProvidedByUsedInOperatingActivities", "USD", LocalDate.of(2025, 8, 1), fyStart, LocalDate.of(2025, 6, 28), "Q3", 78);
        insertNumericFact(q3Doc, "PaymentsToAcquirePropertyPlantAndEquipment", "USD", LocalDate.of(2025, 8, 1), fyStart, LocalDate.of(2025, 6, 28), "Q3", 21);
        insertNumericFact(q3Doc, "CashAndCashEquivalentsAtCarryingValue", "USD", LocalDate.of(2025, 8, 1), null, LocalDate.of(2025, 6, 28), "Q3", 57);
        insertNumericFact(q3Doc, "LongTermDebtCurrent", "USD", LocalDate.of(2025, 8, 1), null, LocalDate.of(2025, 6, 28), "Q3", 7);
        insertNumericFact(q3Doc, "LongTermDebtNoncurrent", "USD", LocalDate.of(2025, 8, 1), null, LocalDate.of(2025, 6, 28), "Q3", 42);
        insertNumericFact(q3Doc, "StockholdersEquity", "USD", LocalDate.of(2025, 8, 1), null, LocalDate.of(2025, 6, 28), "Q3", 22);
        insertNumericFact(q3Doc, "Assets", "USD", LocalDate.of(2025, 8, 1), null, LocalDate.of(2025, 6, 28), "Q3", 72);
        insertNumericFact(q3Doc, "Liabilities", "USD", LocalDate.of(2025, 8, 1), null, LocalDate.of(2025, 6, 28), "Q3", 32);
        insertNumericFact(q3Doc, "EntityCommonStockSharesOutstanding", "shares", LocalDate.of(2025, 8, 1), null, LocalDate.of(2025, 6, 28), "Q3", 10);

        Long fyDoc = insertDocument("0000320193-25-000040", LocalDate.of(2025, 11, 1), LocalDate.of(2025, 9, 27), "10-K");
        insertNumericFact(fyDoc, "RevenueFromContractWithCustomerExcludingAssessedTax", "USD", LocalDate.of(2025, 11, 1), fyStart, LocalDate.of(2025, 9, 27), "FY", 470);
        insertNumericFact(fyDoc, "NetIncomeLoss", "USD", LocalDate.of(2025, 11, 1), fyStart, LocalDate.of(2025, 9, 27), "FY", 94);
        insertNumericFact(fyDoc, "NetCashProvidedByUsedInOperatingActivities", "USD", LocalDate.of(2025, 11, 1), fyStart, LocalDate.of(2025, 9, 27), "FY", 110);
        insertNumericFact(fyDoc, "PaymentsToAcquirePropertyPlantAndEquipment", "USD", LocalDate.of(2025, 11, 1), fyStart, LocalDate.of(2025, 9, 27), "FY", 30);
        insertNumericFact(fyDoc, "CashAndCashEquivalentsAtCarryingValue", "USD", LocalDate.of(2025, 11, 1), null, LocalDate.of(2025, 9, 27), "FY", 58);
        insertNumericFact(fyDoc, "LongTermDebtCurrent", "USD", LocalDate.of(2025, 11, 1), null, LocalDate.of(2025, 9, 27), "FY", 8);
        insertNumericFact(fyDoc, "LongTermDebtNoncurrent", "USD", LocalDate.of(2025, 11, 1), null, LocalDate.of(2025, 9, 27), "FY", 43);
        insertNumericFact(fyDoc, "StockholdersEquity", "USD", LocalDate.of(2025, 11, 1), null, LocalDate.of(2025, 9, 27), "FY", 23);
        insertNumericFact(fyDoc, "Assets", "USD", LocalDate.of(2025, 11, 1), null, LocalDate.of(2025, 9, 27), "FY", 73);
        insertNumericFact(fyDoc, "Liabilities", "USD", LocalDate.of(2025, 11, 1), null, LocalDate.of(2025, 9, 27), "FY", 33);
        insertNumericFact(fyDoc, "EntityCommonStockSharesOutstanding", "shares", LocalDate.of(2025, 11, 1), null, LocalDate.of(2025, 9, 27), "FY", 10);

        UsFinancialStandardizationService.StandardizationSummary summary =
                usFinancialStandardizationService.standardizeTicker("AAPL");

        BigDecimal q2Revenue = fetchStandardizedMetric("Q", "Q2", LocalDate.of(2025, 3, 29), "revenue");
        BigDecimal q3Revenue = fetchStandardizedMetric("Q", "Q3", LocalDate.of(2025, 6, 28), "revenue");
        BigDecimal q2OperatingCashFlow = fetchStandardizedMetric("Q", "Q2", LocalDate.of(2025, 3, 29), "operating_cash_flow");
        BigDecimal q3OperatingCashFlow = fetchStandardizedMetric("Q", "Q3", LocalDate.of(2025, 6, 28), "operating_cash_flow");
        BigDecimal q4Revenue = fetchStandardizedMetric("Q", "Q4", LocalDate.of(2025, 9, 27), "revenue");
        BigDecimal q4FreeCashFlow = fetchStandardizedMetric("Q", "Q4", LocalDate.of(2025, 9, 27), "free_cash_flow");
        BigDecimal ttmRevenue = fetchStandardizedMetric("TTM", "Q4", LocalDate.of(2025, 9, 27), "revenue");
        BigDecimal derivedFcfMargin = jdbcClient.sql("""
                        SELECT fcf_margin
                        FROM fairvalue.financial_derived_metrics
                        WHERE security_id = :securityId
                          AND period_type = 'Q'
                          AND fiscal_period = 'Q4'
                          AND period_end = DATE '2025-09-27'
                        """)
                .param("securityId", aaplSecurityId)
                .query(BigDecimal.class)
                .single();

        assertThat(summary.standardizedRowCount()).isEqualTo(6);
        assertThat(summary.derivedMetricRowCount()).isEqualTo(6);
        assertThat(summary.ttmCount()).isEqualTo(1);
        assertThat(q2Revenue).isEqualByComparingTo("110");
        assertThat(q3Revenue).isEqualByComparingTo("120");
        assertThat(q2OperatingCashFlow).isEqualByComparingTo("26");
        assertThat(q3OperatingCashFlow).isEqualByComparingTo("28");
        assertThat(q4Revenue).isEqualByComparingTo("140");
        assertThat(q4FreeCashFlow).isEqualByComparingTo("23");
        assertThat(ttmRevenue).isEqualByComparingTo("470");
        assertThat(derivedFcfMargin).isEqualByComparingTo("0.164286");
    }

    private void insertQuarter(
            String accessionNo,
            LocalDate filingDate,
            LocalDate periodEnd,
            String fiscalPeriod,
            int revenue,
            int netIncome,
            int operatingCashFlow,
            int capex,
            int cash,
            int shortTermDebt,
            int longTermDebt,
            int equity,
            int assets,
            int liabilities,
            int shares
    ) {
        Long sourceDocumentId = insertDocument(accessionNo, filingDate, periodEnd, "10-Q");

        LocalDate periodStart = periodEnd.minusDays(89);
        insertNumericFact(sourceDocumentId, "Revenues", "USD", filingDate, periodStart, periodEnd, fiscalPeriod, revenue);
        insertNumericFact(sourceDocumentId, "NetIncomeLoss", "USD", filingDate, periodStart, periodEnd, fiscalPeriod, netIncome);
        insertNumericFact(sourceDocumentId, "NetCashProvidedByUsedInOperatingActivities", "USD", filingDate, periodStart, periodEnd, fiscalPeriod, operatingCashFlow);
        insertNumericFact(sourceDocumentId, "PaymentsToAcquirePropertyPlantAndEquipment", "USD", filingDate, periodStart, periodEnd, fiscalPeriod, capex);
        insertNumericFact(sourceDocumentId, "CashAndCashEquivalentsAtCarryingValue", "USD", filingDate, null, periodEnd, fiscalPeriod, cash);
        insertNumericFact(sourceDocumentId, "LongTermDebtCurrent", "USD", filingDate, null, periodEnd, fiscalPeriod, shortTermDebt);
        insertNumericFact(sourceDocumentId, "LongTermDebtNoncurrent", "USD", filingDate, null, periodEnd, fiscalPeriod, longTermDebt);
        insertNumericFact(sourceDocumentId, "StockholdersEquity", "USD", filingDate, null, periodEnd, fiscalPeriod, equity);
        insertNumericFact(sourceDocumentId, "Assets", "USD", filingDate, null, periodEnd, fiscalPeriod, assets);
        insertNumericFact(sourceDocumentId, "Liabilities", "USD", filingDate, null, periodEnd, fiscalPeriod, liabilities);
        insertNumericFact(sourceDocumentId, "EntityCommonStockSharesOutstanding", "shares", filingDate, null, periodEnd, fiscalPeriod, shares);
    }

    private Long insertDocument(String accessionNo, LocalDate filingDate, LocalDate periodEnd, String documentType) {
        return jdbcClient.sql("""
                        INSERT INTO fairvalue.source_documents (
                            security_id,
                            source_id,
                            document_type,
                            document_title,
                            filing_date,
                            period_end_date,
                            accession_no,
                            document_url,
                            parsed_status,
                            parser_version
                        ) VALUES (
                            :securityId,
                            :sourceId,
                            :documentType,
                            'Fixture filing',
                            :filingDate,
                            :periodEnd,
                            :accessionNo,
                            :documentUrl,
                            'parsed',
                            'test-fixture'
                        )
                        RETURNING id
                        """)
                .param("securityId", aaplSecurityId)
                .param("sourceId", secEdgarSourceId)
                .param("documentType", documentType)
                .param("filingDate", filingDate)
                .param("periodEnd", periodEnd)
                .param("accessionNo", accessionNo)
                .param("documentUrl", "https://www.sec.gov/Archives/" + accessionNo + ".htm")
                .query(Long.class)
                .single();
    }

    private BigDecimal fetchStandardizedMetric(String periodType, String fiscalPeriod, LocalDate periodEnd, String column) {
        return jdbcClient.sql("""
                        SELECT %s
                        FROM fairvalue.financial_standardized
                        WHERE security_id = :securityId
                          AND period_type = :periodType
                          AND fiscal_period = :fiscalPeriod
                          AND period_end = :periodEnd
                        """.formatted(column))
                .param("securityId", aaplSecurityId)
                .param("periodType", periodType)
                .param("fiscalPeriod", fiscalPeriod)
                .param("periodEnd", periodEnd)
                .query(BigDecimal.class)
                .single();
    }

    private void insertNumericFact(
            long sourceDocumentId,
            String conceptName,
            String unit,
            LocalDate filingDate,
            LocalDate periodStart,
            LocalDate periodEnd,
            String fiscalPeriod,
            int value
    ) {
        jdbcClient.sql("""
                        INSERT INTO fairvalue.source_document_facts_raw (
                            source_document_id,
                            security_id,
                            taxonomy,
                            concept_name,
                            unit,
                            period_start,
                            period_end,
                            fiscal_year,
                            fiscal_period,
                            value_numeric,
                            decimals,
                            is_custom_tag,
                            raw_json
                        ) VALUES (
                            :sourceDocumentId,
                            :securityId,
                            'us-gaap',
                            :conceptName,
                            :unit,
                            :periodStart,
                            :periodEnd,
                            2025,
                            :fiscalPeriod,
                            :value,
                            0,
                            false,
                            CAST(:rawJson AS jsonb)
                        )
                        """)
                .param("sourceDocumentId", sourceDocumentId)
                .param("securityId", aaplSecurityId)
                .param("conceptName", conceptName)
                .param("unit", unit)
                .param("periodStart", periodStart)
                .param("periodEnd", periodEnd)
                .param("fiscalPeriod", fiscalPeriod)
                .param("value", BigDecimal.valueOf(value))
                .param("rawJson", """
                        {
                          "filed": "%s",
                          "concept": "%s",
                          "unit": "%s"
                        }
                        """.formatted(filingDate, conceptName, unit))
                .update();
    }
}
