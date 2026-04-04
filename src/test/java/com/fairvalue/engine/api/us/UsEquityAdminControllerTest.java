package com.fairvalue.engine.api.us;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairvalue.engine.us.UsCompanyIrClient;
import com.fairvalue.engine.us.UsDamodaranClient;
import com.fairvalue.engine.us.UsFredClient;
import com.fairvalue.engine.us.UsEquityValuationService;
import com.fairvalue.engine.us.UsLongbridgeClient;
import com.fairvalue.engine.us.UsSecClient;
import com.fairvalue.engine.us.UsSecurityMasterService;
import com.fairvalue.engine.us.UsSimfinClient;
import com.fairvalue.engine.us.UsStooqClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "market-data.us.live.enabled=true",
        "market-data.us.live.universe-sync-on-startup=false",
        "app.valuation.us.schedule.max-retries=2",
        "market-data.us.fred.enabled=true",
        "market-data.us.fred.api-key=test-fred-key",
        "market-data.us.simfin.enabled=true",
        "market-data.us.simfin.api-key=test-simfin-key",
        "market-data.us.damodaran.enabled=true"
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

    @MockBean
    private UsStooqClient usStooqClient;

    @MockBean
    private UsFredClient usFredClient;

    @MockBean
    private UsLongbridgeClient usLongbridgeClient;

    @MockBean
    private UsDamodaranClient usDamodaranClient;

    @MockBean
    private UsSimfinClient usSimfinClient;

    @MockBean
    private UsEquityValuationService usEquityValuationService;

    private long aaplSecurityId;

    @BeforeEach
    void setUp() {
        aaplSecurityId = usSecurityMasterService.resolveSecurityId("AAPL").orElseThrow();
        jdbcClient.sql("DELETE FROM fairvalue.valuation_jobs")
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.financial_standardized WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.valuation_latest_snapshot WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("""
                        DELETE FROM fairvalue.report_blocks
                        WHERE valuation_run_id IN (
                            SELECT id
                            FROM fairvalue.valuation_runs
                            WHERE security_id = :securityId
                        )
                        """)
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("""
                        DELETE FROM fairvalue.risk_scores
                        WHERE valuation_run_id IN (
                            SELECT id
                            FROM fairvalue.valuation_runs
                            WHERE security_id = :securityId
                        )
                        """)
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("""
                        DELETE FROM fairvalue.reverse_dcf_results
                        WHERE valuation_run_id IN (
                            SELECT id
                            FROM fairvalue.valuation_runs
                            WHERE security_id = :securityId
                        )
                        """)
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("""
                        DELETE FROM fairvalue.scenario_results
                        WHERE valuation_run_id IN (
                            SELECT id
                            FROM fairvalue.valuation_runs
                            WHERE security_id = :securityId
                        )
                        """)
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("""
                        DELETE FROM fairvalue.valuation_method_results
                        WHERE security_id = :securityId
                        """)
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.valuation_runs WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.financial_derived_metrics WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.financial_quality_scores WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.data_quality_audit WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.market_intraday_snapshot WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.market_snapshot WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.market_price_daily WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.market_data_raw WHERE security_id = :securityId")
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
                .andExpect(jsonPath("$.data_quality_audit_count").value(1))
                .andExpect(jsonPath("$.latest_documents[0].document_type").value("10-Q"))
                .andExpect(jsonPath("$.latest_financial_standardized[0].period_type").value("Q"))
                .andExpect(jsonPath("$.latest_financial_derived_metrics[0].period_type").value("Q"))
                .andExpect(jsonPath("$.latest_financial_quality_scores[0].period_type").value("Q"))
                .andExpect(jsonPath("$.latest_data_quality_audits[0].guidance_status").value("filings_only"));
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

    @Test
    void shouldTriggerMarketSyncAndPersistMarketTables() throws Exception {
        when(usStooqClient.fetchQuote("AAPL")).thenReturn(Optional.of(new UsStooqClient.UsQuote(
                "AAPL",
                java.time.LocalDate.of(2026, 3, 31),
                243.00,
                247.00,
                242.50,
                246.50,
                244.00,
                123456789L
        )));
        when(usSecClient.fetchProfile("AAPL")).thenReturn(Optional.of(new UsSecClient.UsSecProfile(
                "AAPL",
                "Apple Inc.",
                "NASDAQ",
                "AAPL",
                "Electronic Computers",
                java.time.LocalDate.of(2025, 11, 1),
                java.time.LocalDate.of(2026, 2, 1),
                false,
                14600000000.0,
                400000000000.0,
                380000000000.0,
                110000000000.0,
                120000000000.0,
                25000000000.0,
                60000000000.0,
                100000000000.0,
                70000000000.0,
                135000000000.0,
                12000000000.0,
                0.92
        )));

        mockMvc.perform(post("/v1/us-equities-admin/AAPL/market-sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.market_data_raw_count").value(1))
                .andExpect(jsonPath("$.market_price_daily_count").value(1))
                .andExpect(jsonPath("$.market_snapshot_count").value(1))
                .andExpect(jsonPath("$.market_intraday_snapshot_count").value(1));
    }

    @Test
    void shouldBackfillTop50Snapshots() throws Exception {
        when(usEquityValuationService.runValuation(any(), any())).thenAnswer(invocation -> {
            String ticker = invocation.getArgument(0, String.class);
            return new com.fairvalue.engine.api.dto.us.UsValuationRunResponse(
                    ticker,
                    java.util.List.of("dcf"),
                    java.util.List.of(),
                    java.util.List.of(),
                    100.0,
                    new com.fairvalue.engine.api.dto.us.UsFairValueRange(90.0, 100.0, 110.0),
                    0.8,
                    0.1,
                    "balanced",
                    java.util.List.of(),
                    java.util.Map.of(),
                    null,
                    null,
                    null
            );
        });

        mockMvc.perform(post("/v1/us-equities-admin/snapshot-backfill/top50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list_name").value("top50_cross_industry_v1"))
                .andExpect(jsonPath("$.mode").value("run"))
                .andExpect(jsonPath("$.requested_count").value(50))
                .andExpect(jsonPath("$.items.length()").value(50))
                .andExpect(jsonPath("$.completed_count").value(org.hamcrest.Matchers.greaterThan(0)));

        verify(usEquityValuationService).runValuation(eq("AAPL"), any());
    }

    @Test
    void shouldQueueUniverseSnapshotBackfillPage() throws Exception {
        mockMvc.perform(post("/v1/us-equities-admin/snapshot-backfill/universe")
                        .param("page", "1")
                        .param("size", "3")
                        .param("mode", "queue")
                        .param("priority", "240"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list_name").value("us_universe_page_1_size_3"))
                .andExpect(jsonPath("$.mode").value("queue"))
                .andExpect(jsonPath("$.requested_count").value(3))
                .andExpect(jsonPath("$.completed_count").value(3))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].status").value("queued"));

        mockMvc.perform(get("/v1/us-equities-admin/valuation-jobs/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queued_count").value(3));
    }

    @Test
    void shouldReturnExternalSourceStatus() throws Exception {
        when(usFredClient.fetchRiskFreeRate()).thenReturn(Optional.of(
                new UsFredClient.FredSeriesObservation("DGS10", LocalDate.of(2026, 3, 31), 0.043, "fred_api")
        ));
        when(usFredClient.fetchPolicyRate()).thenReturn(Optional.of(
                new UsFredClient.FredSeriesObservation("FEDFUNDS", LocalDate.of(2026, 3, 31), 0.045, "fred_api")
        ));
        when(usDamodaranClient.fetchErpSnapshot()).thenReturn(Optional.of(
                new UsDamodaranClient.DamodaranErpSnapshot(LocalDate.of(2026, 3, 1), 0.043, 0.051, "damodaran_histimpl")
        ));
        when(usSimfinClient.probe()).thenReturn(new UsSimfinClient.SimfinStatus(
                true,
                true,
                false,
                "unauthenticated",
                "Invalid API Key - check the key again and also if you confirmed your e-mail on registration"
        ));
        when(usSimfinClient.fetchCompanyRecord("AAPL")).thenReturn(Optional.of(
                new UsSimfinClient.SimfinCompanyRecord(
                        "AAPL",
                        "111",
                        "Apple Inc.",
                        "101",
                        "US",
                        "SimFin sample",
                        "September",
                        "161000",
                        "0000320193",
                        "USD"
                )
        ));

        mockMvc.perform(get("/v1/us-equities-admin/AAPL/source-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.as_of").exists())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.fred.status").value("ok"))
                .andExpect(jsonPath("$.fred.risk_free_observation.source_label").value("fred_api"))
                .andExpect(jsonPath("$.longbridge.status").exists())
                .andExpect(jsonPath("$.damodaran.status").value("ok"))
                .andExpect(jsonPath("$.simfin.status").value("unauthenticated"))
                .andExpect(jsonPath("$.simfin.dataset_url").doesNotExist())
                .andExpect(jsonPath("$.simfin.company_record.company_name").value("Apple Inc."));
    }

    @Test
    void shouldReturnValuationJobSummaryAndTickerJobs() throws Exception {
        jdbcClient.sql("""
                        INSERT INTO fairvalue.valuation_jobs (
                            job_type,
                            security_id,
                            status,
                            retry_count,
                            payload_json,
                            created_at,
                            started_at,
                            finished_at
                        ) VALUES
                        ('us_scheduled_valuation_refresh', :securityId, 'completed', 0, '{"ticker":"AAPL"}'::jsonb, NOW(), NOW(), NOW()),
                        ('us_scheduled_valuation_refresh', :securityId, 'failed', 1, '{"ticker":"AAPL"}'::jsonb, NOW(), NOW(), NOW())
                        """)
                .param("securityId", aaplSecurityId)
                .update();

        mockMvc.perform(get("/v1/us-equities-admin/valuation-jobs/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed_count").value(1))
                .andExpect(jsonPath("$.failed_count").value(1))
                .andExpect(jsonPath("$.retryable_count").value(1))
                .andExpect(jsonPath("$.repeated_failed_count").value(0))
                .andExpect(jsonPath("$.dead_letter_count").value(0));

        mockMvc.perform(get("/v1/us-equities-admin/AAPL/valuation-jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].job_type").value("us_scheduled_valuation_refresh"));

        mockMvc.perform(get("/v1/us-equities-admin/AAPL/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valuation_job_count").value(2))
                .andExpect(jsonPath("$.latest_valuation_jobs").isArray());
    }

    @Test
    void shouldRetryFailedJobsFromAdminEndpoint() throws Exception {
        Long valuationRunId = jdbcClient.sql("""
                        INSERT INTO fairvalue.valuation_runs (
                            security_id,
                            valuation_date,
                            run_mode,
                            current_price,
                            fair_value_low,
                            fair_value_mid,
                            fair_value_high,
                            blended_intrinsic_value,
                            confidence_level,
                            margin_of_safety,
                            weighted_value,
                            final_verdict,
                            report_json
                        ) VALUES (
                            :securityId,
                            NOW(),
                            'manual',
                            190.0,
                            180.0,
                            200.0,
                            220.0,
                            200.0,
                            0.8,
                            0.1,
                            200.0,
                            'UNDERVALUED',
                            '{}'::jsonb
                        )
                        RETURNING id
                        """)
                .param("securityId", aaplSecurityId)
                .query(Long.class)
                .single();

        jdbcClient.sql("""
                        INSERT INTO fairvalue.valuation_latest_snapshot (
                            security_id,
                            market,
                            latest_run_id,
                            as_of_time,
                            current_price,
                            fair_value_low,
                            fair_value_mid,
                            fair_value_high,
                            upside_pct,
                            confidence_level,
                            margin_of_safety,
                            final_verdict,
                            implied_expectation,
                            value_trap_flag,
                            sector_template,
                            company_type,
                            quality_score,
                            data_quality_score,
                            data_version,
                            summary_json,
                            report_json,
                            source_attribution_json,
                            updated_at
                        ) VALUES (
                            :securityId,
                            'US',
                            :runId,
                            NOW(),
                            190.0,
                            180.0,
                            200.0,
                            220.0,
                            0.05,
                            0.80,
                            0.10,
                            'UNDERVALUED',
                            'balanced',
                            FALSE,
                            'us_tech_compounder',
                            'compounder',
                            0.78,
                            0.82,
                            'test',
                            '{}'::jsonb,
                            '{}'::jsonb,
                            '{}'::jsonb,
                            NOW()
                        )
                        """)
                .param("securityId", aaplSecurityId)
                .param("runId", valuationRunId)
                .update();

        jdbcClient.sql("""
                        INSERT INTO fairvalue.valuation_jobs (
                            job_type,
                            security_id,
                            status,
                            retry_count,
                            payload_json,
                            created_at,
                            started_at,
                            finished_at
                        ) VALUES (
                            'us_scheduled_valuation_refresh',
                            :securityId,
                            'failed',
                            0,
                            '{"ticker":"AAPL","trigger":"scheduled_cycle"}'::jsonb,
                            NOW(),
                            NOW(),
                            NOW()
                        )
                        """)
                .param("securityId", aaplSecurityId)
                .update();

        doAnswer(invocation -> null).when(usEquityValuationService).runScheduledValuation("AAPL");

        mockMvc.perform(post("/v1/us-equities-admin/AAPL/valuation-jobs/retry-failed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].status").value("completed"))
                .andExpect(jsonPath("$[0].valuation_run_id").value(valuationRunId));
    }

    @Test
    void shouldRecoverStaleJobsFromAdminEndpoint() throws Exception {
        jdbcClient.sql("""
                        INSERT INTO fairvalue.valuation_jobs (
                            job_type,
                            security_id,
                            status,
                            retry_count,
                            payload_json,
                            created_at,
                            started_at
                        ) VALUES (
                            'us_scheduled_valuation_refresh',
                            :securityId,
                            'running',
                            0,
                            '{"ticker":"AAPL","trigger":"scheduled_cycle"}'::jsonb,
                            NOW() - INTERVAL '2 hours',
                            NOW() - INTERVAL '2 hours'
                        )
                        """)
                .param("securityId", aaplSecurityId)
                .update();

        mockMvc.perform(post("/v1/us-equities-admin/valuation-jobs/recover-stale"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].status").value("queued"))
                .andExpect(jsonPath("$[0].priority").value(260))
                .andExpect(jsonPath("$[0].error_message").value("stale_running_timeout"));
    }

    @Test
    void shouldExposeRankingCoverageAndDeadLetterViews() throws Exception {
        jdbcClient.sql("""
                        INSERT INTO fairvalue.valuation_jobs (
                            job_type,
                            security_id,
                            status,
                            retry_count,
                            error_message,
                            payload_json,
                            created_at,
                            finished_at
                        ) VALUES (
                            'us_scheduled_valuation_refresh',
                            :securityId,
                            'dead_letter',
                            2,
                            'exhausted retries',
                            '{"ticker":"AAPL"}'::jsonb,
                            NOW(),
                            NOW()
                        )
                        """)
                .param("securityId", aaplSecurityId)
                .update();

        mockMvc.perform(get("/v1/us-equities-admin/ranking-coverage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ranking_mode").exists())
                .andExpect(jsonPath("$.strict_ready").exists())
                .andExpect(jsonPath("$.snapshot_coverage").exists())
                .andExpect(jsonPath("$.rankable_count").exists())
                .andExpect(jsonPath("$.excluded_stale_price_count").exists())
                .andExpect(jsonPath("$.excluded_bad_industry_match_count").exists())
                .andExpect(jsonPath("$.excluded_low_confidence_count").exists());

        mockMvc.perform(get("/v1/us-equities-admin/AAPL/valuation-diagnostics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.rankable").exists())
                .andExpect(jsonPath("$.company_type").exists())
                .andExpect(jsonPath("$.industry_fallback_used").exists())
                .andExpect(jsonPath("$.dcf_outlier_trimmed").exists());

        mockMvc.perform(get("/v1/us-equities-admin/valuation-jobs/dead-letter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].status").value("dead_letter"));
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
