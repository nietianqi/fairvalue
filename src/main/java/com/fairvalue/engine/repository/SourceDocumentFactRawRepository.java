package com.fairvalue.engine.repository;

import com.fairvalue.engine.us.UsRawFactEntry;
import com.fairvalue.engine.us.UsSourceDocumentRawFact;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

@Repository
public class SourceDocumentFactRawRepository {
    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    public SourceDocumentFactRawRepository(JdbcClient jdbcClient, JdbcTemplate jdbcTemplate) {
        this.jdbcClient = jdbcClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<UsRawFactEntry> findBySecurityId(long securityId) {
        return jdbcClient.sql("""
                        SELECT raw.source_document_id,
                               raw.security_id,
                               doc.document_type,
                               doc.filing_date,
                               doc.accession_no,
                               raw.taxonomy,
                               raw.concept_name,
                               raw.unit,
                               raw.period_start,
                               raw.period_end,
                               raw.fiscal_year,
                               raw.fiscal_period,
                               raw.value_numeric,
                               raw.value_text,
                               raw.decimals,
                               raw.context_ref,
                               raw.segment_name,
                               raw.is_custom_tag
                        FROM fairvalue.source_document_facts_raw raw
                        JOIN fairvalue.source_documents doc
                          ON doc.id = raw.source_document_id
                        WHERE raw.security_id = :securityId
                        ORDER BY raw.period_end NULLS LAST, raw.source_document_id, raw.concept_name
                        """)
                .param("securityId", securityId)
                .query(rawFactRowMapper())
                .list();
    }

    public long countBySecurityId(long securityId) {
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.source_document_facts_raw
                        WHERE security_id = :securityId
                        """)
                .param("securityId", securityId)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public List<UsRawFactEntry> findLatestBySecurityId(long securityId, int limit) {
        return jdbcClient.sql("""
                        SELECT raw.source_document_id,
                               raw.security_id,
                               doc.document_type,
                               doc.filing_date,
                               doc.accession_no,
                               raw.taxonomy,
                               raw.concept_name,
                               raw.unit,
                               raw.period_start,
                               raw.period_end,
                               raw.fiscal_year,
                               raw.fiscal_period,
                               raw.value_numeric,
                               raw.value_text,
                               raw.decimals,
                               raw.context_ref,
                               raw.segment_name,
                               raw.is_custom_tag
                        FROM fairvalue.source_document_facts_raw raw
                        JOIN fairvalue.source_documents doc
                          ON doc.id = raw.source_document_id
                        WHERE raw.security_id = :securityId
                        ORDER BY raw.period_end DESC NULLS LAST, raw.source_document_id DESC
                        LIMIT :limit
                        """)
                .param("securityId", securityId)
                .param("limit", limit)
                .query(rawFactRowMapper())
                .list();
    }

    public boolean existsBySecurityIdAndConceptNames(long securityId, List<String> conceptNames) {
        if (conceptNames == null || conceptNames.isEmpty()) {
            return false;
        }
        Boolean exists = jdbcClient.sql("""
                        SELECT EXISTS(
                            SELECT 1
                            FROM fairvalue.source_document_facts_raw
                            WHERE security_id = :securityId
                              AND concept_name IN (:conceptNames)
                        )
                        """)
                .param("securityId", securityId)
                .param("conceptNames", conceptNames)
                .query(Boolean.class)
                .single();
        return Boolean.TRUE.equals(exists);
    }

    public boolean existsBySecurityIdAndConceptKeywords(long securityId, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }

        StringBuilder sql = new StringBuilder("""
                        SELECT EXISTS(
                            SELECT 1
                            FROM fairvalue.source_document_facts_raw
                            WHERE security_id = :securityId
                              AND (
                        """);
        for (int i = 0; i < keywords.size(); i += 1) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("LOWER(concept_name) LIKE :keyword").append(i);
        }
        sql.append("))");

        JdbcClient.StatementSpec spec = jdbcClient.sql(sql.toString())
                .param("securityId", securityId);
        for (int i = 0; i < keywords.size(); i += 1) {
            spec.param("keyword" + i, "%" + keywords.get(i).toLowerCase() + "%");
        }
        Boolean exists = spec.query(Boolean.class).single();
        return Boolean.TRUE.equals(exists);
    }

    public void replaceBySecurityId(long securityId, List<UsSourceDocumentRawFact> facts) {
        jdbcTemplate.update("DELETE FROM fairvalue.source_document_facts_raw WHERE security_id = ?", securityId);
        if (facts.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate("""
                        INSERT INTO fairvalue.source_document_facts_raw (
                            source_document_id,
                            security_id,
                            taxonomy,
                            concept_name,
                            unit,
                            period_start,
                            period_end,
                            fiscal_year,
                            fiscal_period,
                            value_numeric,
                            value_text,
                            decimals,
                            context_ref,
                            segment_name,
                            is_custom_tag,
                            raw_json
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        UsSourceDocumentRawFact fact = facts.get(i);
                        ps.setLong(1, fact.sourceDocumentId());
                        ps.setLong(2, fact.securityId());
                        ps.setString(3, fact.taxonomy());
                        ps.setString(4, fact.conceptName());
                        ps.setString(5, fact.unit());
                        if (fact.periodStart() == null) {
                            ps.setNull(6, Types.DATE);
                        } else {
                            ps.setObject(6, fact.periodStart());
                        }
                        if (fact.periodEnd() == null) {
                            ps.setNull(7, Types.DATE);
                        } else {
                            ps.setObject(7, fact.periodEnd());
                        }
                        if (fact.fiscalYear() == null) {
                            ps.setNull(8, Types.INTEGER);
                        } else {
                            ps.setInt(8, fact.fiscalYear());
                        }
                        ps.setString(9, fact.fiscalPeriod());
                        if (fact.valueNumeric() == null) {
                            ps.setNull(10, Types.NUMERIC);
                        } else {
                            ps.setBigDecimal(10, fact.valueNumeric());
                        }
                        ps.setString(11, fact.valueText());
                        if (fact.decimals() == null) {
                            ps.setNull(12, Types.INTEGER);
                        } else {
                            ps.setInt(12, fact.decimals());
                        }
                        ps.setString(13, fact.contextRef());
                        ps.setString(14, fact.segmentName());
                        ps.setBoolean(15, fact.customTag());
                        PGobject payload = new PGobject();
                        payload.setType("jsonb");
                        payload.setValue(fact.rawJson());
                        ps.setObject(16, payload);
                    }

                    @Override
                    public int getBatchSize() {
                        return facts.size();
                    }
                });
    }

    private RowMapper<UsRawFactEntry> rawFactRowMapper() {
        return (ResultSet rs, int rowNum) -> new UsRawFactEntry(
                rs.getLong("source_document_id"),
                rs.getLong("security_id"),
                rs.getString("document_type"),
                rs.getObject("filing_date", java.time.LocalDate.class),
                rs.getString("accession_no"),
                rs.getString("taxonomy"),
                rs.getString("concept_name"),
                rs.getString("unit"),
                rs.getObject("period_start", java.time.LocalDate.class),
                rs.getObject("period_end", java.time.LocalDate.class),
                rs.getObject("fiscal_year", Integer.class),
                rs.getString("fiscal_period"),
                rs.getBigDecimal("value_numeric"),
                rs.getString("value_text"),
                rs.getObject("decimals", Integer.class),
                rs.getString("context_ref"),
                rs.getString("segment_name"),
                rs.getBoolean("is_custom_tag")
        );
    }
}
