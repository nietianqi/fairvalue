package com.fairvalue.engine.repository;

import com.fairvalue.engine.us.UsFinancialStandardizedRecord;
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

@Repository
public class FinancialStandardizedRepository {
    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    public FinancialStandardizedRepository(JdbcClient jdbcClient, JdbcTemplate jdbcTemplate) {
        this.jdbcClient = jdbcClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void replaceBySecurityId(long securityId, List<UsFinancialStandardizedRecord> records) {
        jdbcTemplate.update("DELETE FROM fairvalue.financial_standardized WHERE security_id = ?", securityId);
        if (records.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate("""
                        INSERT INTO fairvalue.financial_standardized (
                            security_id,
                            period_type,
                            fiscal_year,
                            fiscal_period,
                            period_start,
                            period_end,
                            revenue,
                            gross_profit,
                            ebitda,
                            ebit,
                            net_income,
                            operating_cash_flow,
                            capex,
                            free_cash_flow,
                            cash,
                            short_term_debt,
                            long_term_debt,
                            total_debt,
                            equity,
                            total_assets,
                            total_liabilities,
                            diluted_shares,
                            basic_shares,
                            sbc,
                            lease_liabilities,
                            pension_liabilities,
                            minority_interest,
                            goodwill,
                            intangibles,
                            tax_rate_effective,
                            net_debt,
                            source_document_id,
                            quality_flag_json
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        UsFinancialStandardizedRecord record = records.get(i);
                        ps.setLong(1, record.securityId());
                        ps.setString(2, record.periodType());
                        setInteger(ps, 3, record.fiscalYear());
                        ps.setString(4, record.fiscalPeriod());
                        setDate(ps, 5, record.periodStart());
                        setDate(ps, 6, record.periodEnd());
                        setBigDecimal(ps, 7, record.revenue());
                        setBigDecimal(ps, 8, record.grossProfit());
                        setBigDecimal(ps, 9, record.ebitda());
                        setBigDecimal(ps, 10, record.ebit());
                        setBigDecimal(ps, 11, record.netIncome());
                        setBigDecimal(ps, 12, record.operatingCashFlow());
                        setBigDecimal(ps, 13, record.capex());
                        setBigDecimal(ps, 14, record.freeCashFlow());
                        setBigDecimal(ps, 15, record.cash());
                        setBigDecimal(ps, 16, record.shortTermDebt());
                        setBigDecimal(ps, 17, record.longTermDebt());
                        setBigDecimal(ps, 18, record.totalDebt());
                        setBigDecimal(ps, 19, record.equity());
                        setBigDecimal(ps, 20, record.totalAssets());
                        setBigDecimal(ps, 21, record.totalLiabilities());
                        setBigDecimal(ps, 22, record.dilutedShares());
                        setBigDecimal(ps, 23, record.basicShares());
                        setBigDecimal(ps, 24, record.sbc());
                        setBigDecimal(ps, 25, record.leaseLiabilities());
                        setBigDecimal(ps, 26, record.pensionLiabilities());
                        setBigDecimal(ps, 27, record.minorityInterest());
                        setBigDecimal(ps, 28, record.goodwill());
                        setBigDecimal(ps, 29, record.intangibles());
                        setBigDecimal(ps, 30, record.taxRateEffective());
                        setBigDecimal(ps, 31, record.netDebt());
                        ps.setLong(32, record.sourceDocumentId());
                        PGobject payload = new PGobject();
                        payload.setType("jsonb");
                        payload.setValue(record.qualityFlagJson());
                        ps.setObject(33, payload);
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
                        FROM fairvalue.financial_standardized
                        WHERE security_id = :securityId
                        """)
                .param("securityId", securityId)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public List<UsFinancialStandardizedRecord> findBySecurityId(long securityId) {
        return jdbcClient.sql("""
                        SELECT security_id,
                               period_type,
                               fiscal_year,
                               fiscal_period,
                               period_start,
                               period_end,
                               revenue,
                               gross_profit,
                               ebitda,
                               ebit,
                               net_income,
                               operating_cash_flow,
                               capex,
                               free_cash_flow,
                               cash,
                               short_term_debt,
                               long_term_debt,
                               total_debt,
                               equity,
                               total_assets,
                               total_liabilities,
                               diluted_shares,
                               basic_shares,
                               sbc,
                               lease_liabilities,
                               pension_liabilities,
                               minority_interest,
                               goodwill,
                               intangibles,
                               tax_rate_effective,
                               net_debt,
                               source_document_id,
                               quality_flag_json::text AS quality_flag_json
                        FROM fairvalue.financial_standardized
                        WHERE security_id = :securityId
                        ORDER BY period_end DESC, period_type
                        """)
                .param("securityId", securityId)
                .query(recordRowMapper())
                .list();
    }

    public List<UsFinancialStandardizedRecord> findLatestBySecurityId(long securityId, int limit) {
        return jdbcClient.sql("""
                        SELECT security_id,
                               period_type,
                               fiscal_year,
                               fiscal_period,
                               period_start,
                               period_end,
                               revenue,
                               gross_profit,
                               ebitda,
                               ebit,
                               net_income,
                               operating_cash_flow,
                               capex,
                               free_cash_flow,
                               cash,
                               short_term_debt,
                               long_term_debt,
                               total_debt,
                               equity,
                               total_assets,
                               total_liabilities,
                               diluted_shares,
                               basic_shares,
                               sbc,
                               lease_liabilities,
                               pension_liabilities,
                               minority_interest,
                               goodwill,
                               intangibles,
                               tax_rate_effective,
                               net_debt,
                               source_document_id,
                               quality_flag_json::text AS quality_flag_json
                        FROM fairvalue.financial_standardized
                        WHERE security_id = :securityId
                        ORDER BY period_end DESC, period_type
                        LIMIT :limit
                        """)
                .param("securityId", securityId)
                .param("limit", limit)
                .query(recordRowMapper())
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

    private RowMapper<UsFinancialStandardizedRecord> recordRowMapper() {
        return (ResultSet rs, int rowNum) -> new UsFinancialStandardizedRecord(
                rs.getLong("security_id"),
                rs.getString("period_type"),
                rs.getObject("fiscal_year", Integer.class),
                rs.getString("fiscal_period"),
                rs.getObject("period_start", java.time.LocalDate.class),
                rs.getObject("period_end", java.time.LocalDate.class),
                rs.getBigDecimal("revenue"),
                rs.getBigDecimal("gross_profit"),
                rs.getBigDecimal("ebitda"),
                rs.getBigDecimal("ebit"),
                rs.getBigDecimal("net_income"),
                rs.getBigDecimal("operating_cash_flow"),
                rs.getBigDecimal("capex"),
                rs.getBigDecimal("free_cash_flow"),
                rs.getBigDecimal("cash"),
                rs.getBigDecimal("short_term_debt"),
                rs.getBigDecimal("long_term_debt"),
                rs.getBigDecimal("total_debt"),
                rs.getBigDecimal("equity"),
                rs.getBigDecimal("total_assets"),
                rs.getBigDecimal("total_liabilities"),
                rs.getBigDecimal("diluted_shares"),
                rs.getBigDecimal("basic_shares"),
                rs.getBigDecimal("sbc"),
                rs.getBigDecimal("lease_liabilities"),
                rs.getBigDecimal("pension_liabilities"),
                rs.getBigDecimal("minority_interest"),
                rs.getBigDecimal("goodwill"),
                rs.getBigDecimal("intangibles"),
                rs.getBigDecimal("tax_rate_effective"),
                rs.getBigDecimal("net_debt"),
                rs.getLong("source_document_id"),
                rs.getString("quality_flag_json")
        );
    }
}
