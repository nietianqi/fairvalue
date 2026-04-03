package com.fairvalue.engine.api.us;

import com.fairvalue.engine.us.UsSecurityMasterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UsEquityControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private UsSecurityMasterService usSecurityMasterService;

    private long aaplSecurityId;
    private long stooqSourceId;

    @BeforeEach
    void setUp() {
        aaplSecurityId = usSecurityMasterService.resolveSecurityId("AAPL").orElseThrow();
        stooqSourceId = jdbcClient.sql("""
                        SELECT id
                        FROM fairvalue.source_registry
                        WHERE source_name = 'stooq'
                        """)
                .query(Long.class)
                .single();

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
                        DELETE FROM fairvalue.valuation_method_results
                        WHERE security_id = :securityId
                        """)
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.valuation_runs WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.market_price_daily WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.market_snapshot WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
    }

    @Test
    void shouldReturnUsProfile() throws Exception {
        mockMvc.perform(get("/v1/us-equities/AAPL/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.sector_template").value("us_tech_compounder"))
                .andExpect(jsonPath("$.company_type").value("compounder"));

        mockMvc.perform(get("/v1/us-equities/AAPL/data-quality"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.guidance_status").exists())
                .andExpect(jsonPath("$.confidence_level").exists());

        mockMvc.perform(get("/v1/us-equities/AAPL/financial-quality"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.total_quality_score").exists());
    }

    @Test
    void shouldRunUsValuationWithMultiMethods() throws Exception {
        String payload = """
                {
                  "style": "balanced",
                  "horizon": "6-18m",
                  "useConsensus": true,
                  "forceMethods": ["dcf", "reverse_dcf", "ev_ebitda"],
                  "customAssumptions": {
                    "wacc": 0.085,
                    "terminal_growth": 0.028
                  }
                }
                """;

        mockMvc.perform(post("/v1/us-equities/AAPL/valuation/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valuation_methods.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.method_outputs.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.scenario_matrix.length()").value(3))
                .andExpect(jsonPath("$.risk_matrix.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.decision.source_attribution.erp_source").isNotEmpty());
    }

    @Test
    void shouldRunUsValuationWithDefaultRequestWhenBodyIsMissing() throws Exception {
        mockMvc.perform(post("/v1/us-equities/AAPL/valuation/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.summary.fair_value_range.mid").exists());
    }

    @Test
    void shouldPersistValuationArtifactsForRun() throws Exception {
        insertMarketHistory(LocalDate.of(2026, 3, 27), 225.00, 29.0);
        insertMarketHistory(LocalDate.of(2026, 3, 28), 229.00, 27.0);
        insertMarketHistory(LocalDate.of(2026, 3, 31), 232.00, 25.0);

        String payload = """
                {
                  "style": "balanced",
                  "horizon": "6-18m",
                  "useConsensus": true,
                  "forceMethods": ["dcf", "reverse_dcf", "relative_valuation", "historical_multiple"],
                  "customAssumptions": {
                    "wacc": 0.085,
                    "terminal_growth": 0.028
                  }
                }
                """;

        mockMvc.perform(post("/v1/us-equities/AAPL/valuation/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"));

        Long runCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.valuation_runs
                        WHERE security_id = :securityId
                        """)
                .param("securityId", aaplSecurityId)
                .query(Long.class)
                .single();
        assertThat(runCount).isEqualTo(1L);

        List<String> methods = jdbcClient.sql("""
                        SELECT method_name
                        FROM fairvalue.valuation_method_results
                        WHERE security_id = :securityId
                        ORDER BY method_name
                        """)
                .param("securityId", aaplSecurityId)
                .query(String.class)
                .list();
        assertThat(methods).containsExactlyInAnyOrder("dcf", "historical_multiple", "relative_valuation", "reverse_dcf");

        String historicalAssumptions = jdbcClient.sql("""
                        SELECT assumptions_json::text
                        FROM fairvalue.valuation_method_results
                        WHERE security_id = :securityId
                          AND method_name = 'historical_multiple'
                        """)
                .param("securityId", aaplSecurityId)
                .query(String.class)
                .single();
        assertThat(historicalAssumptions).contains("market_price_daily");
        assertThat(historicalAssumptions).contains("\"daily_rows\": 3");

        Long scenarioCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.scenario_results
                        WHERE valuation_run_id IN (
                            SELECT id
                            FROM fairvalue.valuation_runs
                            WHERE security_id = :securityId
                        )
                        """)
                .param("securityId", aaplSecurityId)
                .query(Long.class)
                .single();
        assertThat(scenarioCount).isEqualTo(3L);

        Long reverseCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.reverse_dcf_results
                        WHERE valuation_run_id IN (
                            SELECT id
                            FROM fairvalue.valuation_runs
                            WHERE security_id = :securityId
                        )
                        """)
                .param("securityId", aaplSecurityId)
                .query(Long.class)
                .single();
        assertThat(reverseCount).isEqualTo(1L);

        List<String> adjustmentTypes = jdbcClient.sql("""
                        SELECT DISTINCT adjustment_type
                        FROM fairvalue.risk_scores
                        WHERE valuation_run_id IN (
                            SELECT id
                            FROM fairvalue.valuation_runs
                            WHERE security_id = :securityId
                        )
                        ORDER BY adjustment_type
                        """)
                .param("securityId", aaplSecurityId)
                .query(String.class)
                .list();
        assertThat(adjustmentTypes).contains("mos", "scenario_weight", "wacc");
    }

    @Test
    void shouldReturnUsSummaryAndReport() throws Exception {
        mockMvc.perform(get("/v1/us-equities/AAPL/valuation/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buy_zone.low").exists())
                .andExpect(jsonPath("$.fair_value_range.mid").exists());

        mockMvc.perform(get("/v1/us-equities/AAPL/valuation/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.one_line_verdict").exists())
                .andExpect(jsonPath("$.valuation_breakdown.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }

    @Test
    void peersEndpointShouldNotCreateValuationRuns() throws Exception {
        Long before = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.valuation_runs
                        WHERE security_id = :securityId
                        """)
                .param("securityId", aaplSecurityId)
                .query(Long.class)
                .single();

        mockMvc.perform(get("/v1/peers/US/AAPL")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.market").value("US"))
                .andExpect(jsonPath("$.source_mode").exists());

        Long after = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.valuation_runs
                        WHERE security_id = :securityId
                        """)
                .param("securityId", aaplSecurityId)
                .query(Long.class)
                .single();

        assertThat(after).isEqualTo(before);
    }

    private void insertMarketHistory(LocalDate tradeDate, double close, double pe) {
        Instant snapshotTime = tradeDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        jdbcClient.sql("""
                        INSERT INTO fairvalue.market_price_daily (
                            security_id,
                            trade_date,
                            open,
                            high,
                            low,
                            close,
                            volume,
                            turnover,
                            adj_close,
                            dividend_adjustment_factor,
                            split_adjustment_factor,
                            source_id
                        ) VALUES (
                            :securityId,
                            :tradeDate,
                            :close,
                            :close,
                            :close,
                            :close,
                            1000,
                            :turnover,
                            :close,
                            1,
                            1,
                            :sourceId
                        )
                        """)
                .param("securityId", aaplSecurityId)
                .param("tradeDate", tradeDate)
                .param("close", close)
                .param("turnover", close * 1000)
                .param("sourceId", stooqSourceId)
                .update();

        jdbcClient.sql("""
                        INSERT INTO fairvalue.market_snapshot (
                            security_id,
                            snapshot_time,
                            last_price,
                            prev_close,
                            open_price,
                            high_price,
                            low_price,
                            volume,
                            turnover,
                            market_cap_vendor,
                            pe_ttm_vendor,
                            pb_vendor,
                            dividend_yield_vendor,
                            eps_ttm_vendor,
                            bps_vendor,
                            float_shares_vendor,
                            total_shares_vendor,
                            source_id
                        ) VALUES (
                            :securityId,
                            :snapshotTime,
                            :close,
                            :close,
                            :close,
                            :close,
                            :close,
                            1000,
                            :turnover,
                            1000000000,
                            :pe,
                            10,
                            0.005,
                            8,
                            20,
                            1000000,
                            1000000,
                            :sourceId
                        )
                        """)
                .param("securityId", aaplSecurityId)
                .param("snapshotTime", java.sql.Timestamp.from(snapshotTime))
                .param("close", close)
                .param("turnover", close * 1000)
                .param("pe", pe)
                .param("sourceId", stooqSourceId)
                .update();
    }
}
