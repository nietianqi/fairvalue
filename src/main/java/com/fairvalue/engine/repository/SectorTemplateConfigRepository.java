package com.fairvalue.engine.repository;

import com.fairvalue.engine.us.UsSectorTemplateConfigRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SectorTemplateConfigRepository {
    private final JdbcClient jdbcClient;

    public SectorTemplateConfigRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<UsSectorTemplateConfigRecord> findBySectorTemplate(String sectorTemplate) {
        return jdbcClient.sql("""
                        SELECT sector_template,
                               primary_methods_json::text AS primary_methods_json,
                               forbidden_methods_json::text AS forbidden_methods_json,
                               default_weights_json::text AS default_weights_json,
                               safety_margin_rule_json::text AS safety_margin_rule_json,
                               risk_notes_json::text AS risk_notes_json
                        FROM fairvalue.sector_template_config
                        WHERE sector_template = :sectorTemplate
                        """)
                .param("sectorTemplate", sectorTemplate)
                .query((rs, rowNum) -> new UsSectorTemplateConfigRecord(
                        rs.getString("sector_template"),
                        rs.getString("primary_methods_json"),
                        rs.getString("forbidden_methods_json"),
                        rs.getString("default_weights_json"),
                        rs.getString("safety_margin_rule_json"),
                        rs.getString("risk_notes_json")
                ))
                .optional();
    }
}
