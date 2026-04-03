package com.fairvalue.engine.api;

import com.fairvalue.engine.us.UsSecurityMasterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UsValuationHistoryControllerTest {
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
        jdbcClient.sql("DELETE FROM fairvalue.valuation_method_results WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.market_price_daily WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
        jdbcClient.sql("DELETE FROM fairvalue.valuation_runs WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
    }

    @Test
    void shouldUsePersistedUsHistoryInsteadOfSyntheticWave() throws Exception {
        insertMarketPrice(LocalDate.of(2026, 3, 27), 220.0);
        insertMarketPrice(LocalDate.of(2026, 3, 28), 230.0);
        insertMarketPrice(LocalDate.of(2026, 3, 31), 245.0);

        long firstRunId = insertValuationRun(LocalDate.of(2026, 3, 27), 240.0);
        long secondRunId = insertValuationRun(LocalDate.of(2026, 3, 31), 250.0);

        mockMvc.perform(get("/v1/valuation/history/US/AAPL").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.market").value("US"))
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.points.length()").value(3))
                .andExpect(jsonPath("$.points[0].date").value("2026-03-27"))
                .andExpect(jsonPath("$.points[0].close_price").value(220.0))
                .andExpect(jsonPath("$.points[0].tradable_fair_value").value(240.0))
                .andExpect(jsonPath("$.points[0].valuation_run_date").value("2026-03-27"))
                .andExpect(jsonPath("$.points[0].run_id").value(String.valueOf(firstRunId)))
                .andExpect(jsonPath("$.points[0].simulated").value(false))
                .andExpect(jsonPath("$.points[1].date").value("2026-03-28"))
                .andExpect(jsonPath("$.points[1].tradable_fair_value").value(240.0))
                .andExpect(jsonPath("$.points[1].valuation_run_date").value("2026-03-27"))
                .andExpect(jsonPath("$.points[1].run_id").value(String.valueOf(firstRunId)))
                .andExpect(jsonPath("$.points[1].simulated").value(false))
                .andExpect(jsonPath("$.points[2].date").value("2026-03-31"))
                .andExpect(jsonPath("$.points[2].close_price").value(245.0))
                .andExpect(jsonPath("$.points[2].tradable_fair_value").value(250.0))
                .andExpect(jsonPath("$.points[2].valuation_run_date").value("2026-03-31"))
                .andExpect(jsonPath("$.points[2].run_id").value(String.valueOf(secondRunId)))
                .andExpect(jsonPath("$.points[2].simulated").value(false));
    }

    private void insertMarketPrice(LocalDate tradeDate, double close) {
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
                .param("close", BigDecimal.valueOf(close))
                .param("turnover", BigDecimal.valueOf(close * 1000))
                .param("sourceId", stooqSourceId)
                .update();
    }

    private long insertValuationRun(LocalDate valuationDate, double fairValueMid) {
        Instant instant = valuationDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Long insertedId = jdbcClient.sql("""
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
                            implied_expectation_label,
                            sector_template,
                            company_type,
                            weighted_value,
                            final_verdict,
                            buy_zone_low,
                            buy_zone_high,
                            hold_zone_low,
                            hold_zone_high,
                            avoid_zone_low,
                            avoid_zone_high,
                            report_json
                        ) VALUES (
                            :securityId,
                            :valuationDate,
                            'manual',
                            :currentPrice,
                            :fairValueLow,
                            :fairValueMid,
                            :fairValueHigh,
                            :fairValueMid,
                            0.80,
                            0.25,
                            'balanced',
                            'us_tech_compounder',
                            'compounder',
                            :fairValueMid,
                            'BUY',
                            :fairValueLow,
                            :fairValueMid,
                            :fairValueMid,
                            :fairValueHigh,
                            :fairValueHigh,
                            :fairValueHigh,
                            '{}'::jsonb
                        )
                        RETURNING id
                        """)
                .param("securityId", aaplSecurityId)
                .param("valuationDate", java.sql.Timestamp.from(instant))
                .param("currentPrice", BigDecimal.valueOf(fairValueMid * 0.9))
                .param("fairValueLow", BigDecimal.valueOf(fairValueMid * 0.92))
                .param("fairValueMid", BigDecimal.valueOf(fairValueMid))
                .param("fairValueHigh", BigDecimal.valueOf(fairValueMid * 1.08))
                .query(Long.class)
                .single();
        return insertedId == null ? 0L : insertedId;
    }
}
