package com.fairvalue.engine.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IngestionJobRepository {
    private final JdbcClient jdbcClient;

    public IngestionJobRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long startJob(String jobType, String payloadJson) {
        return jdbcClient.sql("""
                        INSERT INTO fairvalue.ingestion_jobs (
                            job_type,
                            status,
                            started_at,
                            payload_json
                        ) VALUES (
                            :jobType,
                            'running',
                            NOW(),
                            CAST(:payloadJson AS jsonb)
                        )
                        RETURNING id
                        """)
                .param("jobType", jobType)
                .param("payloadJson", payloadJson)
                .query(Long.class)
                .single();
    }

    public void finishJob(long jobId, String status, int retryCount, String errorMessage, String payloadJson) {
        jdbcClient.sql("""
                        UPDATE fairvalue.ingestion_jobs
                        SET status = :status,
                            retry_count = :retryCount,
                            error_message = :errorMessage,
                            finished_at = NOW(),
                            payload_json = CAST(:payloadJson AS jsonb)
                        WHERE id = :jobId
                        """)
                .param("jobId", jobId)
                .param("status", status)
                .param("retryCount", retryCount)
                .param("errorMessage", errorMessage)
                .param("payloadJson", payloadJson)
                .update();
    }
}
