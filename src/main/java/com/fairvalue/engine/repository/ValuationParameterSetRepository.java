package com.fairvalue.engine.repository;

import com.fairvalue.engine.us.UsValuationParameterRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class ValuationParameterSetRepository {
    private final JdbcClient jdbcClient;

    public ValuationParameterSetRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<UsValuationParameterRecord> findActiveBySectorTemplate(String sectorTemplate, LocalDate effectiveDate) {
        return jdbcClient.sql("""
                        SELECT security_id,
                               sector_template,
                               parameter_type,
                               parameter_key,
                               parameter_value::text AS parameter_value_json
                        FROM fairvalue.valuation_parameter_set
                        WHERE sector_template = :sectorTemplate
                          AND security_id IS NULL
                          AND (effective_from IS NULL OR effective_from <= :effectiveDate)
                          AND (effective_to IS NULL OR effective_to >= :effectiveDate)
                        ORDER BY parameter_type, parameter_key
                        """)
                .param("sectorTemplate", sectorTemplate)
                .param("effectiveDate", effectiveDate)
                .query((rs, rowNum) -> new UsValuationParameterRecord(
                        rs.getObject("security_id", Long.class),
                        rs.getString("sector_template"),
                        rs.getString("parameter_type"),
                        rs.getString("parameter_key"),
                        rs.getString("parameter_value_json")
                ))
                .list();
    }

    public List<UsValuationParameterRecord> findActiveBySecurityId(long securityId, LocalDate effectiveDate) {
        return jdbcClient.sql("""
                        SELECT security_id,
                               sector_template,
                               parameter_type,
                               parameter_key,
                               parameter_value::text AS parameter_value_json
                        FROM fairvalue.valuation_parameter_set
                        WHERE security_id = :securityId
                          AND (effective_from IS NULL OR effective_from <= :effectiveDate)
                          AND (effective_to IS NULL OR effective_to >= :effectiveDate)
                        ORDER BY parameter_type, parameter_key
                        """)
                .param("securityId", securityId)
                .param("effectiveDate", effectiveDate)
                .query((rs, rowNum) -> new UsValuationParameterRecord(
                        rs.getObject("security_id", Long.class),
                        rs.getString("sector_template"),
                        rs.getString("parameter_type"),
                        rs.getString("parameter_key"),
                        rs.getString("parameter_value_json")
                ))
                .list();
    }
}
