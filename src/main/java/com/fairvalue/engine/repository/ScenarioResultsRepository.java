package com.fairvalue.engine.repository;

import com.fairvalue.engine.us.UsScenarioResultRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ScenarioResultsRepository {
    private final JdbcClient jdbcClient;

    public ScenarioResultsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insertAll(List<UsScenarioResultRecord> records) {
        for (UsScenarioResultRecord record : records) {
            jdbcClient.sql("""
                            INSERT INTO fairvalue.scenario_results (
                                valuation_run_id,
                                scenario_name,
                                probability_weight,
                                price_target,
                                revenue_cagr,
                                ebitda_margin,
                                fcf_margin,
                                notes_json
                            ) VALUES (
                                :valuationRunId,
                                :scenarioName,
                                :probabilityWeight,
                                :priceTarget,
                                :revenueCagr,
                                :ebitdaMargin,
                                :fcfMargin,
                                CAST(:notesJson AS jsonb)
                            )
                            ON CONFLICT (valuation_run_id, scenario_name)
                            DO UPDATE SET
                                probability_weight = EXCLUDED.probability_weight,
                                price_target = EXCLUDED.price_target,
                                revenue_cagr = EXCLUDED.revenue_cagr,
                                ebitda_margin = EXCLUDED.ebitda_margin,
                                fcf_margin = EXCLUDED.fcf_margin,
                                notes_json = EXCLUDED.notes_json
                            """)
                    .param("valuationRunId", record.valuationRunId())
                    .param("scenarioName", record.scenarioName())
                    .param("probabilityWeight", record.probabilityWeight())
                    .param("priceTarget", record.priceTarget())
                    .param("revenueCagr", record.revenueCagr())
                    .param("ebitdaMargin", record.ebitdaMargin())
                    .param("fcfMargin", record.fcfMargin())
                    .param("notesJson", emptyJson(record.notesJson()))
                    .update();
        }
    }

    private String emptyJson(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }
}
