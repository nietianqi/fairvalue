package com.fairvalue.engine.repository;

import com.fairvalue.engine.us.UsReverseDcfResultRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReverseDcfResultsRepository {
    private final JdbcClient jdbcClient;

    public ReverseDcfResultsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void upsert(UsReverseDcfResultRecord record) {
        jdbcClient.sql("""
                        INSERT INTO fairvalue.reverse_dcf_results (
                            valuation_run_id,
                            implied_revenue_cagr,
                            implied_ebitda_margin,
                            implied_fcf_margin,
                            terminal_growth,
                            wacc,
                            implied_expectation_label,
                            notes_json
                        ) VALUES (
                            :valuationRunId,
                            :impliedRevenueCagr,
                            :impliedEbitdaMargin,
                            :impliedFcfMargin,
                            :terminalGrowth,
                            :wacc,
                            :impliedExpectationLabel,
                            CAST(:notesJson AS jsonb)
                        )
                        ON CONFLICT (valuation_run_id)
                        DO UPDATE SET
                            implied_revenue_cagr = EXCLUDED.implied_revenue_cagr,
                            implied_ebitda_margin = EXCLUDED.implied_ebitda_margin,
                            implied_fcf_margin = EXCLUDED.implied_fcf_margin,
                            terminal_growth = EXCLUDED.terminal_growth,
                            wacc = EXCLUDED.wacc,
                            implied_expectation_label = EXCLUDED.implied_expectation_label,
                            notes_json = EXCLUDED.notes_json
                        """)
                .param("valuationRunId", record.valuationRunId())
                .param("impliedRevenueCagr", record.impliedRevenueCagr())
                .param("impliedEbitdaMargin", record.impliedEbitdaMargin())
                .param("impliedFcfMargin", record.impliedFcfMargin())
                .param("terminalGrowth", record.terminalGrowth())
                .param("wacc", record.wacc())
                .param("impliedExpectationLabel", record.impliedExpectationLabel())
                .param("notesJson", emptyJson(record.notesJson()))
                .update();
    }

    private String emptyJson(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }
}
