package com.fairvalue.engine.repository;

import com.fairvalue.engine.us.UsRiskScoreRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RiskScoresRepository {
    private final JdbcClient jdbcClient;

    public RiskScoresRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insertAll(List<UsRiskScoreRecord> records) {
        for (UsRiskScoreRecord record : records) {
            jdbcClient.sql("""
                            INSERT INTO fairvalue.risk_scores (
                                security_id,
                                valuation_run_id,
                                risk_type,
                                probability,
                                impact,
                                score,
                                adjustment_type,
                                adjustment_value,
                                note
                            ) VALUES (
                                :securityId,
                                :valuationRunId,
                                :riskType,
                                :probability,
                                :impact,
                                :score,
                                :adjustmentType,
                                :adjustmentValue,
                                :note
                            )
                            """)
                    .param("securityId", record.securityId())
                    .param("valuationRunId", record.valuationRunId())
                    .param("riskType", record.riskType())
                    .param("probability", record.probability())
                    .param("impact", record.impact())
                    .param("score", record.score())
                    .param("adjustmentType", record.adjustmentType())
                    .param("adjustmentValue", record.adjustmentValue())
                    .param("note", record.note())
                    .update();
        }
    }
}
