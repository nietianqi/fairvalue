package com.fairvalue.engine.repository;

import com.fairvalue.engine.us.UsDataQualityAuditRecord;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Repository
public class DataQualityAuditRepository {
    private final JdbcClient jdbcClient;

    public DataQualityAuditRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void upsert(UsDataQualityAuditRecord record) {
        jdbcClient.sql("""
                        INSERT INTO fairvalue.data_quality_audit (
                            security_id,
                            audit_date,
                            latest_10k_date,
                            latest_10q_date,
                            has_recent_8k,
                            share_count_verified,
                            sbc_quantified,
                            guidance_status,
                            net_debt_updated,
                            tax_rate_updated,
                            litigation_captured,
                            convertible_identified,
                            data_quality_score,
                            confidence_level,
                            missing_items_json,
                            warning_flags_json
                        ) VALUES (
                            :securityId,
                            :auditDate,
                            :latest10kDate,
                            :latest10qDate,
                            :hasRecent8k,
                            :shareCountVerified,
                            :sbcQuantified,
                            :guidanceStatus,
                            :netDebtUpdated,
                            :taxRateUpdated,
                            :litigationCaptured,
                            :convertibleIdentified,
                            :dataQualityScore,
                            :confidenceLevel,
                            CAST(:missingItemsJson AS jsonb),
                            CAST(:warningFlagsJson AS jsonb)
                        )
                        ON CONFLICT (security_id, audit_date)
                        DO UPDATE SET
                            latest_10k_date = EXCLUDED.latest_10k_date,
                            latest_10q_date = EXCLUDED.latest_10q_date,
                            has_recent_8k = EXCLUDED.has_recent_8k,
                            share_count_verified = EXCLUDED.share_count_verified,
                            sbc_quantified = EXCLUDED.sbc_quantified,
                            guidance_status = EXCLUDED.guidance_status,
                            net_debt_updated = EXCLUDED.net_debt_updated,
                            tax_rate_updated = EXCLUDED.tax_rate_updated,
                            litigation_captured = EXCLUDED.litigation_captured,
                            convertible_identified = EXCLUDED.convertible_identified,
                            data_quality_score = EXCLUDED.data_quality_score,
                            confidence_level = EXCLUDED.confidence_level,
                            missing_items_json = EXCLUDED.missing_items_json,
                            warning_flags_json = EXCLUDED.warning_flags_json
                        """)
                .param("securityId", record.securityId())
                .param("auditDate", record.auditDate())
                .param("latest10kDate", record.latest10kDate())
                .param("latest10qDate", record.latest10qDate())
                .param("hasRecent8k", record.hasRecent8k())
                .param("shareCountVerified", record.shareCountVerified())
                .param("sbcQuantified", record.sbcQuantified())
                .param("guidanceStatus", record.guidanceStatus())
                .param("netDebtUpdated", record.netDebtUpdated())
                .param("taxRateUpdated", record.taxRateUpdated())
                .param("litigationCaptured", record.litigationCaptured())
                .param("convertibleIdentified", record.convertibleIdentified())
                .param("dataQualityScore", record.dataQualityScore())
                .param("confidenceLevel", record.confidenceLevel())
                .param("missingItemsJson", jsonOrEmptyArray(record.missingItemsJson()))
                .param("warningFlagsJson", jsonOrEmptyArray(record.warningFlagsJson()))
                .update();
    }

    public long countBySecurityId(long securityId) {
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.data_quality_audit
                        WHERE security_id = :securityId
                        """)
                .param("securityId", securityId)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public Optional<UsDataQualityAuditRecord> findLatestBySecurityId(long securityId) {
        return jdbcClient.sql("""
                        SELECT security_id,
                               audit_date,
                               latest_10k_date,
                               latest_10q_date,
                               has_recent_8k,
                               share_count_verified,
                               sbc_quantified,
                               guidance_status,
                               net_debt_updated,
                               tax_rate_updated,
                               litigation_captured,
                               convertible_identified,
                               data_quality_score,
                               confidence_level,
                               missing_items_json::text AS missing_items_json,
                               warning_flags_json::text AS warning_flags_json
                        FROM fairvalue.data_quality_audit
                        WHERE security_id = :securityId
                        ORDER BY audit_date DESC, created_at DESC
                        LIMIT 1
                        """)
                .param("securityId", securityId)
                .query(rowMapper())
                .optional();
    }

    public List<UsDataQualityAuditRecord> findLatestBySecurityId(long securityId, int limit) {
        return jdbcClient.sql("""
                        SELECT security_id,
                               audit_date,
                               latest_10k_date,
                               latest_10q_date,
                               has_recent_8k,
                               share_count_verified,
                               sbc_quantified,
                               guidance_status,
                               net_debt_updated,
                               tax_rate_updated,
                               litigation_captured,
                               convertible_identified,
                               data_quality_score,
                               confidence_level,
                               missing_items_json::text AS missing_items_json,
                               warning_flags_json::text AS warning_flags_json
                        FROM fairvalue.data_quality_audit
                        WHERE security_id = :securityId
                        ORDER BY audit_date DESC, created_at DESC
                        LIMIT :limit
                        """)
                .param("securityId", securityId)
                .param("limit", limit)
                .query(rowMapper())
                .list();
    }

    private String jsonOrEmptyArray(String value) {
        return value == null || value.isBlank() ? "[]" : value;
    }

    private RowMapper<UsDataQualityAuditRecord> rowMapper() {
        return (rs, rowNum) -> new UsDataQualityAuditRecord(
                rs.getLong("security_id"),
                rs.getObject("audit_date", java.time.LocalDate.class),
                rs.getObject("latest_10k_date", java.time.LocalDate.class),
                rs.getObject("latest_10q_date", java.time.LocalDate.class),
                rs.getBoolean("has_recent_8k"),
                rs.getBoolean("share_count_verified"),
                rs.getBoolean("sbc_quantified"),
                rs.getString("guidance_status"),
                rs.getBoolean("net_debt_updated"),
                rs.getBoolean("tax_rate_updated"),
                rs.getBoolean("litigation_captured"),
                rs.getBoolean("convertible_identified"),
                rs.getBigDecimal("data_quality_score"),
                rs.getBigDecimal("confidence_level"),
                rs.getString("missing_items_json"),
                rs.getString("warning_flags_json")
        );
    }
}
