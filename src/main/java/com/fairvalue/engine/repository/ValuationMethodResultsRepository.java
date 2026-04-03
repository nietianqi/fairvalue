package com.fairvalue.engine.repository;

import com.fairvalue.engine.us.UsLatestRelativeValuationRecord;
import com.fairvalue.engine.us.UsValuationMethodResultRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class ValuationMethodResultsRepository {
    private final JdbcClient jdbcClient;

    public ValuationMethodResultsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insertAll(List<UsValuationMethodResultRecord> records) {
        for (UsValuationMethodResultRecord record : records) {
            jdbcClient.sql("""
                            INSERT INTO fairvalue.valuation_method_results (
                                valuation_run_id,
                                security_id,
                                method_name,
                                bear_value,
                                base_value,
                                bull_value,
                                weight,
                                is_primary_method,
                                input_snapshot_date,
                                assumptions_json,
                                sensitivity_json,
                                notes
                            ) VALUES (
                                :valuationRunId,
                                :securityId,
                                :methodName,
                                :bearValue,
                                :baseValue,
                                :bullValue,
                                :weight,
                                :primaryMethod,
                                :inputSnapshotDate,
                                CAST(:assumptionsJson AS jsonb),
                                CAST(:sensitivityJson AS jsonb),
                                :notes
                            )
                            ON CONFLICT (valuation_run_id, method_name)
                            DO UPDATE SET
                                bear_value = EXCLUDED.bear_value,
                                base_value = EXCLUDED.base_value,
                                bull_value = EXCLUDED.bull_value,
                                weight = EXCLUDED.weight,
                                is_primary_method = EXCLUDED.is_primary_method,
                                input_snapshot_date = EXCLUDED.input_snapshot_date,
                                assumptions_json = EXCLUDED.assumptions_json,
                                sensitivity_json = EXCLUDED.sensitivity_json,
                                notes = EXCLUDED.notes
                            """)
                    .param("valuationRunId", record.valuationRunId())
                    .param("securityId", record.securityId())
                    .param("methodName", record.methodName())
                    .param("bearValue", record.bearValue())
                    .param("baseValue", record.baseValue())
                    .param("bullValue", record.bullValue())
                    .param("weight", record.weight())
                    .param("primaryMethod", record.primaryMethod())
                    .param("inputSnapshotDate", record.inputSnapshotDate())
                    .param("assumptionsJson", emptyJson(record.assumptionsJson()))
                    .param("sensitivityJson", emptyJson(record.sensitivityJson()))
                    .param("notes", record.notes())
                    .update();
        }
    }

    public Optional<UsLatestRelativeValuationRecord> findLatestRelativeBySecurityId(long securityId) {
        return jdbcClient.sql("""
                        SELECT vmr.valuation_run_id,
                               vr.valuation_date,
                               vmr.assumptions_json::text AS assumptions_json
                        FROM fairvalue.valuation_method_results vmr
                        JOIN fairvalue.valuation_runs vr
                          ON vr.id = vmr.valuation_run_id
                        WHERE vmr.security_id = :securityId
                          AND vmr.method_name = 'relative_valuation'
                        ORDER BY vr.valuation_date DESC
                        LIMIT 1
                        """)
                .param("securityId", securityId)
                .query((rs, rowNum) -> new UsLatestRelativeValuationRecord(
                        rs.getLong("valuation_run_id"),
                        rs.getTimestamp("valuation_date").toInstant(),
                        rs.getString("assumptions_json")
                ))
                .optional();
    }

    private String emptyJson(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }
}
