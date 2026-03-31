package com.fairvalue.engine.repository;

import com.fairvalue.engine.us.UsFinancialQualityScoreRecord;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Repository
public class FinancialQualityScoresRepository {
    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    public FinancialQualityScoresRepository(JdbcClient jdbcClient, JdbcTemplate jdbcTemplate) {
        this.jdbcClient = jdbcClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void replaceBySecurityId(long securityId, List<UsFinancialQualityScoreRecord> records) {
        jdbcTemplate.update("DELETE FROM fairvalue.financial_quality_scores WHERE security_id = ?", securityId);
        if (records.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate("""
                        INSERT INTO fairvalue.financial_quality_scores (
                            security_id,
                            period_type,
                            period_end,
                            earnings_quality_score,
                            revenue_quality_score,
                            balance_sheet_score,
                            capital_efficiency_score,
                            capital_allocation_score,
                            total_quality_score,
                            quality_score_breakdown_json,
                            red_flags_json,
                            forensic_flags_json
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        UsFinancialQualityScoreRecord record = records.get(i);
                        ps.setLong(1, record.securityId());
                        ps.setString(2, record.periodType());
                        ps.setObject(3, record.periodEnd());
                        setBigDecimal(ps, 4, record.earningsQualityScore());
                        setBigDecimal(ps, 5, record.revenueQualityScore());
                        setBigDecimal(ps, 6, record.balanceSheetScore());
                        setBigDecimal(ps, 7, record.capitalEfficiencyScore());
                        setBigDecimal(ps, 8, record.capitalAllocationScore());
                        setBigDecimal(ps, 9, record.totalQualityScore());
                        setJsonb(ps, 10, record.qualityScoreBreakdownJson());
                        setJsonb(ps, 11, record.redFlagsJson());
                        setJsonb(ps, 12, record.forensicFlagsJson());
                    }

                    @Override
                    public int getBatchSize() {
                        return records.size();
                    }
                });
    }

    public long countBySecurityId(long securityId) {
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.financial_quality_scores
                        WHERE security_id = :securityId
                        """)
                .param("securityId", securityId)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public List<UsFinancialQualityScoreRecord> findLatestBySecurityId(long securityId, int limit) {
        return jdbcClient.sql("""
                        SELECT security_id,
                               period_type,
                               period_end,
                               earnings_quality_score,
                               revenue_quality_score,
                               balance_sheet_score,
                               capital_efficiency_score,
                               capital_allocation_score,
                               total_quality_score,
                               quality_score_breakdown_json::text AS quality_score_breakdown_json,
                               red_flags_json::text AS red_flags_json,
                               forensic_flags_json::text AS forensic_flags_json
                        FROM fairvalue.financial_quality_scores
                        WHERE security_id = :securityId
                        ORDER BY period_end DESC, period_type
                        LIMIT :limit
                        """)
                .param("securityId", securityId)
                .param("limit", limit)
                .query(rowMapper())
                .list();
    }

    public Optional<UsFinancialQualityScoreRecord> findLatestAvailableBySecurityId(long securityId) {
        return jdbcClient.sql("""
                        SELECT security_id,
                               period_type,
                               period_end,
                               earnings_quality_score,
                               revenue_quality_score,
                               balance_sheet_score,
                               capital_efficiency_score,
                               capital_allocation_score,
                               total_quality_score,
                               quality_score_breakdown_json::text AS quality_score_breakdown_json,
                               red_flags_json::text AS red_flags_json,
                               forensic_flags_json::text AS forensic_flags_json
                        FROM fairvalue.financial_quality_scores
                        WHERE security_id = :securityId
                        ORDER BY period_end DESC, period_type
                        LIMIT 1
                        """)
                .param("securityId", securityId)
                .query(rowMapper())
                .optional();
    }

    private void setBigDecimal(PreparedStatement ps, int index, java.math.BigDecimal value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.NUMERIC);
        } else {
            ps.setBigDecimal(index, value);
        }
    }

    private void setJsonb(PreparedStatement ps, int index, String payload) throws SQLException {
        PGobject object = new PGobject();
        object.setType("jsonb");
        object.setValue(payload == null ? "{}" : payload);
        ps.setObject(index, object);
    }

    private RowMapper<UsFinancialQualityScoreRecord> rowMapper() {
        return (ResultSet rs, int rowNum) -> new UsFinancialQualityScoreRecord(
                rs.getLong("security_id"),
                rs.getString("period_type"),
                rs.getObject("period_end", java.time.LocalDate.class),
                rs.getBigDecimal("earnings_quality_score"),
                rs.getBigDecimal("revenue_quality_score"),
                rs.getBigDecimal("balance_sheet_score"),
                rs.getBigDecimal("capital_efficiency_score"),
                rs.getBigDecimal("capital_allocation_score"),
                rs.getBigDecimal("total_quality_score"),
                rs.getString("quality_score_breakdown_json"),
                rs.getString("red_flags_json"),
                rs.getString("forensic_flags_json")
        );
    }
}
