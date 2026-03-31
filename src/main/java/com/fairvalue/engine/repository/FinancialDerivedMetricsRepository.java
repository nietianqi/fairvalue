package com.fairvalue.engine.repository;

import com.fairvalue.engine.us.UsFinancialDerivedMetricRecord;
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

@Repository
public class FinancialDerivedMetricsRepository {
    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    public FinancialDerivedMetricsRepository(JdbcClient jdbcClient, JdbcTemplate jdbcTemplate) {
        this.jdbcClient = jdbcClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void replaceBySecurityId(long securityId, List<UsFinancialDerivedMetricRecord> records) {
        jdbcTemplate.update("DELETE FROM fairvalue.financial_derived_metrics WHERE security_id = ?", securityId);
        if (records.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate("""
                        INSERT INTO fairvalue.financial_derived_metrics (
                            security_id,
                            period_type,
                            fiscal_year,
                            fiscal_period,
                            period_end,
                            gross_margin,
                            ebit_margin,
                            fcf_margin,
                            roe,
                            roic,
                            roa,
                            fcf_conversion,
                            accruals_ratio,
                            net_debt_to_ebitda,
                            interest_coverage,
                            working_capital_ratio,
                            book_value_per_share,
                            eps_diluted,
                            owner_earnings_estimate,
                            altman_z_score
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        UsFinancialDerivedMetricRecord record = records.get(i);
                        ps.setLong(1, record.securityId());
                        ps.setString(2, record.periodType());
                        setInteger(ps, 3, record.fiscalYear());
                        ps.setString(4, record.fiscalPeriod());
                        setDate(ps, 5, record.periodEnd());
                        setBigDecimal(ps, 6, record.grossMargin());
                        setBigDecimal(ps, 7, record.ebitMargin());
                        setBigDecimal(ps, 8, record.fcfMargin());
                        setBigDecimal(ps, 9, record.roe());
                        setBigDecimal(ps, 10, record.roic());
                        setBigDecimal(ps, 11, record.roa());
                        setBigDecimal(ps, 12, record.fcfConversion());
                        setBigDecimal(ps, 13, record.accrualsRatio());
                        setBigDecimal(ps, 14, record.netDebtToEbitda());
                        setBigDecimal(ps, 15, record.interestCoverage());
                        setBigDecimal(ps, 16, record.workingCapitalRatio());
                        setBigDecimal(ps, 17, record.bookValuePerShare());
                        setBigDecimal(ps, 18, record.epsDiluted());
                        setBigDecimal(ps, 19, record.ownerEarningsEstimate());
                        setBigDecimal(ps, 20, record.altmanZScore());
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
                        FROM fairvalue.financial_derived_metrics
                        WHERE security_id = :securityId
                        """)
                .param("securityId", securityId)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public List<UsFinancialDerivedMetricRecord> findLatestBySecurityId(long securityId, int limit) {
        return jdbcClient.sql("""
                        SELECT security_id,
                               period_type,
                               fiscal_year,
                               fiscal_period,
                               period_end,
                               gross_margin,
                               ebit_margin,
                               fcf_margin,
                               roe,
                               roic,
                               roa,
                               fcf_conversion,
                               accruals_ratio,
                               net_debt_to_ebitda,
                               interest_coverage,
                               working_capital_ratio,
                               book_value_per_share,
                               eps_diluted,
                               owner_earnings_estimate,
                               altman_z_score
                        FROM fairvalue.financial_derived_metrics
                        WHERE security_id = :securityId
                        ORDER BY period_end DESC, period_type
                        LIMIT :limit
                        """)
                .param("securityId", securityId)
                .param("limit", limit)
                .query(rowMapper())
                .list();
    }

    private void setBigDecimal(PreparedStatement ps, int index, java.math.BigDecimal value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.NUMERIC);
        } else {
            ps.setBigDecimal(index, value);
        }
    }

    private void setInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private void setDate(PreparedStatement ps, int index, java.time.LocalDate value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.DATE);
        } else {
            ps.setObject(index, value);
        }
    }

    private RowMapper<UsFinancialDerivedMetricRecord> rowMapper() {
        return (ResultSet rs, int rowNum) -> new UsFinancialDerivedMetricRecord(
                rs.getLong("security_id"),
                rs.getString("period_type"),
                rs.getObject("fiscal_year", Integer.class),
                rs.getString("fiscal_period"),
                rs.getObject("period_end", java.time.LocalDate.class),
                rs.getBigDecimal("gross_margin"),
                rs.getBigDecimal("ebit_margin"),
                rs.getBigDecimal("fcf_margin"),
                rs.getBigDecimal("roe"),
                rs.getBigDecimal("roic"),
                rs.getBigDecimal("roa"),
                rs.getBigDecimal("fcf_conversion"),
                rs.getBigDecimal("accruals_ratio"),
                rs.getBigDecimal("net_debt_to_ebitda"),
                rs.getBigDecimal("interest_coverage"),
                rs.getBigDecimal("working_capital_ratio"),
                rs.getBigDecimal("book_value_per_share"),
                rs.getBigDecimal("eps_diluted"),
                rs.getBigDecimal("owner_earnings_estimate"),
                rs.getBigDecimal("altman_z_score")
        );
    }
}
