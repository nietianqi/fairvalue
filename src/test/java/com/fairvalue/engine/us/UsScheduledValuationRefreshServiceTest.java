package com.fairvalue.engine.us;

import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.domain.StockFundamentals;
import com.fairvalue.engine.domain.StockSnapshot;
import com.fairvalue.engine.repository.ValuationJobRepository;
import com.fairvalue.engine.repository.ValuationLatestSnapshotRepository;
import com.fairvalue.engine.service.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "app.valuation.us.schedule.enabled=false",
        "market-data.us.live.enabled=false",
        "app.valuation.us.schedule.max-retries=2"
})
class UsScheduledValuationRefreshServiceTest {
    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ValuationJobRepository valuationJobRepository;

    @Autowired
    private ValuationLatestSnapshotRepository valuationLatestSnapshotRepository;

    @Autowired
    private UsSecurityMasterService usSecurityMasterService;

    @Autowired
    private UsValuationJobService usValuationJobService;

    @MockBean
    private MarketDataService marketDataService;

    @MockBean
    private UsEquityValuationService usEquityValuationService;

    @MockBean
    private UsValuationAlertService usValuationAlertService;

    private long aaplSecurityId;

    @BeforeEach
    void setUp() {
        aaplSecurityId = usSecurityMasterService.resolveSecurityId("AAPL").orElseThrow();
        jdbcClient.sql("DELETE FROM fairvalue.valuation_jobs")
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.valuation_latest_snapshot WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
    }

    @Test
    void shouldRetryFailedJobsWithoutDuplicatingFreshTickerJob() {
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

        Long persistedRunId = jdbcClient.sql("""
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
                            'scheduled',
                            190.0,
                            180.0,
                            200.0,
                            220.0,
                            200.0,
                            0.80,
                            0.10,
                            200.0,
                            'Fairly valued',
                            '{}'::jsonb
                        )
                        RETURNING id
                        """)
                .param("securityId", aaplSecurityId)
                .query(Long.class)
                .single();

        valuationLatestSnapshotRepository.upsert(new UsValuationLatestSnapshotRecord(
                aaplSecurityId,
                "US",
                persistedRunId,
                Instant.now(),
                190.0,
                180.0,
                200.0,
                220.0,
                0.05,
                0.80,
                0.10,
                "Fairly valued",
                "balanced",
                false,
                "us_tech_compounder",
                "compounder",
                0.75,
                0.82,
                "test",
                "{}",
                "{}",
                "{}"
        ));

        when(marketDataService.listSnapshots(eq(List.of(Market.US)))).thenReturn(List.of(
                new StockSnapshot(
                        Market.US,
                        "AAPL",
                        "USD",
                        "Apple Inc.",
                        "Technology",
                        190.0,
                        new StockFundamentals(0.08, 0.22, 0.09, 0.03, 0.30, 28.0, 20.0, 18.0, 0.005, 0.01, 0.02, 0.1, 0.0, 0.8, 0.0, 0.02, 0.1, 2.0, 0.9, 0.8, 0.0, 0.8, true),
                        "test"
                )
        ));
        doAnswer(invocation -> null).when(usEquityValuationService).runScheduledValuation("AAPL");

        UsScheduledValuationRefreshService service = new UsScheduledValuationRefreshService(
                marketDataService,
                usSecurityMasterService,
                usValuationJobService,
                usValuationAlertService,
                10,
                2,
                24,
                90,
                10
        );

        service.refreshActiveUniverse();
        service.processQueuedJobs();

        List<UsValuationJobRecord> aaplJobs = usValuationJobService.recentJobsForTicker("AAPL", 10);
        assertEquals(1, aaplJobs.size());
        assertEquals("completed", aaplJobs.get(0).status());
        assertEquals(persistedRunId, aaplJobs.get(0).valuationRunId());
    }

    @Test
    void shouldRetryFailedJobsOnDemand() {
        Long persistedRunId = jdbcClient.sql("""
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
                            195.0,
                            180.0,
                            210.0,
                            225.0,
                            210.0,
                            0.84,
                            0.08,
                            210.0,
                            'UNDERVALUED',
                            '{}'::jsonb
                        )
                        RETURNING id
                        """)
                .param("securityId", aaplSecurityId)
                .query(Long.class)
                .single();

        valuationLatestSnapshotRepository.upsert(new UsValuationLatestSnapshotRecord(
                aaplSecurityId,
                "US",
                persistedRunId,
                Instant.now(),
                195.0,
                180.0,
                210.0,
                225.0,
                0.08,
                0.84,
                0.08,
                "UNDERVALUED",
                "balanced",
                false,
                "us_tech_compounder",
                "compounder",
                0.77,
                0.82,
                "test",
                "{}",
                "{}",
                "{}"
        ));

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

        List<UsValuationJobService.JobExecutionResult> retried = usValuationJobService.retryFailedJobs(2, 24, 10);

        assertEquals(1, retried.size());
        assertEquals("completed", retried.get(0).status());

        List<UsValuationJobRecord> jobs = valuationJobRepository.findRecent(UsValuationJobService.JOB_TYPE, 5);
        assertEquals("completed", jobs.get(0).status());
        assertEquals(persistedRunId, jobs.get(0).valuationRunId());
    }

    @Test
    void shouldRecoverStaleRunningJobsBeforeRefreshCycle() {
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

        Long persistedRunId = jdbcClient.sql("""
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
                            'scheduled',
                            190.0,
                            180.0,
                            200.0,
                            220.0,
                            200.0,
                            0.80,
                            0.10,
                            200.0,
                            'Fairly valued',
                            '{}'::jsonb
                        )
                        RETURNING id
                        """)
                .param("securityId", aaplSecurityId)
                .query(Long.class)
                .single();

        valuationLatestSnapshotRepository.upsert(new UsValuationLatestSnapshotRecord(
                aaplSecurityId,
                "US",
                persistedRunId,
                Instant.now(),
                190.0,
                180.0,
                200.0,
                220.0,
                0.05,
                0.80,
                0.10,
                "Fairly valued",
                "balanced",
                false,
                "us_tech_compounder",
                "compounder",
                0.75,
                0.82,
                "test",
                "{}",
                "{}",
                "{}"
        ));

        when(marketDataService.listSnapshots(eq(List.of(Market.US)))).thenReturn(List.of(
                new StockSnapshot(
                        Market.US,
                        "AAPL",
                        "USD",
                        "Apple Inc.",
                        "Technology",
                        190.0,
                        new StockFundamentals(0.08, 0.22, 0.09, 0.03, 0.30, 28.0, 20.0, 18.0, 0.005, 0.01, 0.02, 0.1, 0.0, 0.8, 0.0, 0.02, 0.1, 2.0, 0.9, 0.8, 0.0, 0.8, true),
                        "test"
                )
        ));
        doAnswer(invocation -> null).when(usEquityValuationService).runScheduledValuation("AAPL");

        UsScheduledValuationRefreshService service = new UsScheduledValuationRefreshService(
                marketDataService,
                usSecurityMasterService,
                usValuationJobService,
                usValuationAlertService,
                10,
                2,
                24,
                90,
                10
        );

        service.recoverStaleJobs();
        service.refreshActiveUniverse();
        service.processQueuedJobs();

        List<UsValuationJobRecord> aaplJobs = usValuationJobService.recentJobsForTicker("AAPL", 10);
        assertEquals(1, aaplJobs.size());
        assertEquals("queued", aaplJobs.get(0).status());
        assertEquals(1, aaplJobs.get(0).retryCount());
        assertEquals("stale_running_timeout", aaplJobs.get(0).errorMessage());
    }
}
