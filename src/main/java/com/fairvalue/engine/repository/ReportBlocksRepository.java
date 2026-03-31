package com.fairvalue.engine.repository;

import com.fairvalue.engine.us.UsReportBlockRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ReportBlocksRepository {
    private final JdbcClient jdbcClient;

    public ReportBlocksRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insertAll(List<UsReportBlockRecord> records) {
        for (UsReportBlockRecord record : records) {
            jdbcClient.sql("""
                            INSERT INTO fairvalue.report_blocks (
                                valuation_run_id,
                                block_type,
                                block_content,
                                display_order
                            ) VALUES (
                                :valuationRunId,
                                :blockType,
                                CAST(:blockContentJson AS jsonb),
                                :displayOrder
                            )
                            ON CONFLICT (valuation_run_id, block_type)
                            DO UPDATE SET
                                block_content = EXCLUDED.block_content,
                                display_order = EXCLUDED.display_order
                            """)
                    .param("valuationRunId", record.valuationRunId())
                    .param("blockType", record.blockType())
                    .param("blockContentJson", emptyJson(record.blockContentJson()))
                    .param("displayOrder", record.displayOrder())
                    .update();
        }
    }

    private String emptyJson(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }
}
