package com.fairvalue.engine.api.us;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairvalue.engine.us.UsCompanyIrClient;
import com.fairvalue.engine.us.UsSecClient;
import com.fairvalue.engine.us.UsSecurityMasterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "market-data.us.live.enabled=true",
        "market-data.us.live.universe-sync-on-startup=false"
})
@AutoConfigureMockMvc
class UsEquityAdminControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private UsSecurityMasterService usSecurityMasterService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UsSecClient usSecClient;

    @MockBean
    private UsCompanyIrClient usCompanyIrClient;

    private long aaplSecurityId;

    @BeforeEach
    void setUp() {
        aaplSecurityId = usSecurityMasterService.resolveSecurityId("AAPL").orElseThrow();
        jdbcClient.sql("DELETE FROM fairvalue.financial_standardized WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.financial_derived_metrics WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.financial_quality_scores WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.source_document_facts_raw WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("""
                        DELETE FROM fairvalue.source_documents
                        WHERE security_id = :securityId
                          AND source_id IN (
                              SELECT id
                              FROM fairvalue.source_registry
                              WHERE source_name IN ('sec_edgar', 'company_ir')
                          )
                        """)
                .param("securityId", aaplSecurityId)
                .update();
    }

    @Test
    void shouldTriggerSecSyncFromAdminEndpoint() throws Exception {
        JsonNode submissions = objectMapper.readTree("""
                {
                  "cik": "320193",
                  "filings": {
                    "recent": {
                      "form": ["10-K"],
                      "accessionNumber": ["0000320193-25-000123"],
                      "filingDate": ["2025-11-01"],
                      "acceptanceDateTime": ["2025-11-01T06:01:36.000Z"],
                      "reportDate": ["2025-09-27"],
                      "primaryDocument": ["aapl-20250927.htm"],
                      "primaryDocDescription": ["Annual report"]
                    }
                  }
                }
                """);
        JsonNode companyFacts = objectMapper.readTree("""
                {
                  "facts": {
                    "us-gaap": {
                      "Revenues": {
                        "units": {
                          "USD": [
                            {
                              "accn": "0000320193-25-000123",
                              "form": "10-K",
                              "fy": 2025,
                              "fp": "FY",
                              "filed": "2025-11-01",
                              "start": "2024-09-29",
                              "end": "2025-09-27",
                              "val": 391035000000
                            }
                          ]
                        }
                      }
                    }
                  }
                }
                """);

        when(usSecClient.fetchSubmissions("AAPL")).thenReturn(Optional.of(submissions));
        when(usSecClient.fetchCompanyFacts("AAPL")).thenReturn(Optional.of(companyFacts));

        mockMvc.perform(post("/v1/us-equities-admin/AAPL/sec-sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.security_id").value(aaplSecurityId))
                .andExpect(jsonPath("$.submission_documents_upserted").value(1))
                .andExpect(jsonPath("$.raw_facts_inserted").value(1))
                .andExpect(jsonPath("$.status").value("completed"));
    }

    @Test
    void shouldStandardizeAndReturnOverviewFromAdminEndpoints() throws Exception {
        long sourceId = jdbcClient.sql("""
                        SELECT id
                        FROM fairvalue.source_registry
                        WHERE source_name = 'sec_edgar'
                        """)
                .query(Long.class)
                .single();

        insertQuarterDocument(sourceId, "0000320193-25-000010", "2025-02-01", "2024-12-28", "Q1", 100, 20, 24, 6, 55, 5, 40, 20, 70, 30, 10);
        insertQuarterDocument(sourceId, "0000320193-25-000020", "2025-05-02", "2025-03-29", "Q2", 110, 22, 26, 7, 56, 6, 41, 21, 71, 31, 10);
        insertQuarterDocument(sourceId, "0000320193-25-000030", "2025-08-01", "2025-06-28", "Q3", 120, 24, 28, 8, 57, 7, 42, 22, 72, 32, 10);
        insertQuarterDocument(sourceId, "0000320193-25-000040", "2025-11-01", "2025-09-27", "Q4", 130, 26, 30, 9, 58, 8, 43, 23, 73, 33, 10);

        mockMvc.perform(post("/v1/us-equities-admin/AAPL/standardize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.raw_fact_count").value(44))
                .andExpect(jsonPath("$.standardized_row_count").value(5))
                .andExpect(jsonPath("$.derived_metric_row_count").value(5))
                .andExpect(jsonPath("$.financial_quality_row_count").value(5))
                .andExpect(jsonPath("$.quarter_count").value(4))
                .andExpect(jsonPath("$.ttm_count").value(1))
                .andExpect(jsonPath("$.status").value("completed"));

        mockMvc.perform(get("/v1/us-equities-admin/AAPL/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.source_document_count").value(4))
                .andExpect(jsonPath("$.company_ir_document_count").value(0))
                .andExpect(jsonPath("$.raw_fact_count").value(44))
                .andExpect(jsonPath("$.financial_standardized_count").value(5))
                .andExpect(jsonPath("$.financial_derived_metric_count").value(5))
                .andExpect(jsonPath("$.financial_quality_score_count").value(5))
                .andExpect(jsonPath("$.latest_documents[0].document_type").value("10-Q"))
                .andExpect(jsonPath("$.latest_financial_standardized[0].period_type").value("Q"))
                .andExpect(jsonPath("$.latest_financial_derived_metrics[0].period_type").value("Q"))
                .andExpect(jsonPath("$.latest_financial_quality_scores[0].period_type").value("Q"));
    }

    @Test
    void shouldTriggerCompanyIrSyncFromAdminEndpoint() throws Exception {
        when(usCompanyIrClient.fetch("https://www.apple.com/newsroom/rss-feed.rss")).thenReturn(Optional.of("""
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <entry>
                    <updated>2026-03-20T12:00:00Z</updated>
                    <category term="PRESS RELEASE"/>
                    <title>Apple reports fiscal first quarter results</title>
                    <link href="https://www.apple.com/newsroom/2026/03/apple-reports-fiscal-first-quarter-results/"/>
                  </entry>
                </feed>
                """));

        mockMvc.perform(post("/v1/us-equities-admin/AAPL/ir-sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.sources_visited").value(1))
                .andExpect(jsonPath("$.documents_upserted").value(1))
                .andExpect(jsonPath("$.document_type_counts.EARNINGS_RELEASE").value(1))
                .andExpect(jsonPath("$.status").value("completed"));

        mockMvc.perform(get("/v1/us-equities-admin/AAPL/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company_ir_document_count").value(1))
                .andExpect(jsonPath("$.latest_company_ir_documents[0].document_type").value("EARNINGS_RELEASE"));
    }

    private void insertQuarterDocument(
            long sourceId,
            String accessionNo,
            String filingDate,
            String periodEnd,
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
        Long sourceDocumentId = jdbcClient.sql("""
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
                            '10-Q',
                            'Quarterly report',
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
                .param("sourceId", sourceId)
                .param("filingDate", java.time.LocalDate.parse(filingDate))
                .param("periodEnd", java.time.LocalDate.parse(periodEnd))
                .param("accessionNo", accessionNo)
                .param("documentUrl", "https://www.sec.gov/Archives/" + accessionNo + ".htm")
                .query(Long.class)
                .single();

        insertNumericFact(sourceDocumentId, "Revenues", "USD", filingDate, periodEnd, fiscalPeriod, revenue, java.time.LocalDate.parse(periodEnd).minusDays(89));
        insertNumericFact(sourceDocumentId, "NetIncomeLoss", "USD", filingDate, periodEnd, fiscalPeriod, netIncome, java.time.LocalDate.parse(periodEnd).minusDays(89));
        insertNumericFact(sourceDocumentId, "NetCashProvidedByUsedInOperatingActivities", "USD", filingDate, periodEnd, fiscalPeriod, operatingCashFlow, java.time.LocalDate.parse(periodEnd).minusDays(89));
        insertNumericFact(sourceDocumentId, "PaymentsToAcquirePropertyPlantAndEquipment", "USD", filingDate, periodEnd, fiscalPeriod, capex, java.time.LocalDate.parse(periodEnd).minusDays(89));
        insertNumericFact(sourceDocumentId, "CashAndCashEquivalentsAtCarryingValue", "USD", filingDate, periodEnd, fiscalPeriod, cash, null);
        insertNumericFact(sourceDocumentId, "LongTermDebtCurrent", "USD", filingDate, periodEnd, fiscalPeriod, shortTermDebt, null);
        insertNumericFact(sourceDocumentId, "LongTermDebtNoncurrent", "USD", filingDate, periodEnd, fiscalPeriod, longTermDebt, null);
        insertNumericFact(sourceDocumentId, "StockholdersEquity", "USD", filingDate, periodEnd, fiscalPeriod, equity, null);
        insertNumericFact(sourceDocumentId, "Assets", "USD", filingDate, periodEnd, fiscalPeriod, assets, null);
        insertNumericFact(sourceDocumentId, "Liabilities", "USD", filingDate, periodEnd, fiscalPeriod, liabilities, null);
        insertNumericFact(sourceDocumentId, "EntityCommonStockSharesOutstanding", "shares", filingDate, periodEnd, fiscalPeriod, shares, null);
    }

    private void insertNumericFact(
            long sourceDocumentId,
            String conceptName,
            String unit,
            String filed,
            String periodEnd,
            String fiscalPeriod,
            int value,
            java.time.LocalDate periodStart
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
                .param("periodEnd", java.time.LocalDate.parse(periodEnd))
                .param("fiscalPeriod", fiscalPeriod)
                .param("value", java.math.BigDecimal.valueOf(value))
                .param("rawJson", """
                        {
                          "filed": "%s",
                          "concept": "%s",
                          "unit": "%s"
                        }
                        """.formatted(filed, conceptName, unit))
                .update();
    }
}
